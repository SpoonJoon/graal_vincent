package jdk.graal.compiler.phases.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.*;
import jdk.graal.compiler.nodes.calc.*;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.replacements.SnippetTemplate;

public class MethodAtomicInstrumentationPhase extends BasePhase<HighTierContext> {
 
    private static final List<String> BENCHMARK_NAMES = Arrays.asList(
        "sunflow", "batik", "derby", "eclipse", "fop", "jfree", "menalto", "sablecc", "xalan", "pmd"
    );

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (!shouldInstrument(graph)) {
            return;
        }

        try {
            Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode idNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));

            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            FixedNode ogStartNext = graph.start().next();


                        // Get the FrameState from the start node
            // FrameState frameState = graph.start().stateAfter();

            // Atomic read and increment of sample counter
            ResolvedJavaField sampleCounterField = context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("sampleCounter"));
            long sampleCounterOffset = sampleCounterField.getOffset();
            ValueNode sampleCounterOffsetNode = graph.addWithoutUnique(ConstantNode.forLong(sampleCounterOffset));
            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AtomicReadAndWriteNode atomicIncrement = graph.add(new AtomicReadAndWriteNode(null, sampleCounterOffsetNode, oneConstantNode, JavaKind.Int, LocationIdentity.any()));
            // atomicIncrement.setStateAfter(frameState);
            graph.addAfterFixed(graph.start(), atomicIncrement);

            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1000), StampFactory.forKind(JavaKind.Int)));
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(atomicIncrement, sampleRateNode));

            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            ForeignCallNode startTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addAfterFixed(instrumentationBegin, startTime);

            // Atomic read and increment of buffer pointer
            ResolvedJavaField bufferPointerField = context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("bufferIndex"));
            long bufferPointerOffset = bufferPointerField.getOffset();
            ValueNode bufferPointerOffsetNode = graph.addWithoutUnique(ConstantNode.forLong(bufferPointerOffset));
            AtomicReadAndWriteNode atomicPointerIncrement = graph.add(new AtomicReadAndWriteNode(null, bufferPointerOffsetNode, oneConstantNode, JavaKind.Int, LocationIdentity.any()));
            // atomicPointerIncrement.setStateAfter(frameState);
            graph.addAfterFixed(startTime, atomicPointerIncrement);

            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("Buffer"))));
            graph.addAfterFixed(atomicPointerIncrement, readBuffer);

            StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, atomicPointerIncrement, null, null, JavaKind.Long, idNode));
            graph.addAfterFixed(readBuffer, writeToBufferID);

            AddNode incrementPointer = graph.addWithoutUnique(new AddNode(atomicPointerIncrement, oneConstantNode));
            StoreIndexedNode writeStartTime = graph.add(new StoreIndexedNode(readBuffer, incrementPointer, null, null, JavaKind.Long, startTime));
            graph.addAfterFixed(writeToBufferID, writeStartTime);

            atomicIncrement.setNext(ifNode);
            merge.setNext(ogStartNext);

            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                instrumentReturnNode(graph, context, returnNode, startTime, idNode);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation failed: " + e.getMessage(), e);
        }
    }

    private boolean shouldInstrument(StructuredGraph graph) {
        String className = graph.method().getDeclaringClass().getName().replace('/', '.').toLowerCase();
        return BENCHMARK_NAMES.stream().anyMatch(className::contains);
    }

    private void instrumentReturnNode(StructuredGraph graph, HighTierContext context, ReturnNode returnNode, ForeignCallNode startTime, ValueNode idNode) {
        try {
            FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
            if (predecessor == null) {
                throw new RuntimeException("Return node has no predecessor");
            }

            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            // Get the FrameState from the start node
            // FrameState frameState = graph.start().stateAfter();

            // Atomic read and increment of sample counter
            ResolvedJavaField sampleCounterField = context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("sampleCounter"));
            long sampleCounterOffset = sampleCounterField.getOffset();
            ValueNode sampleCounterOffsetNode = graph.addWithoutUnique(ConstantNode.forLong(sampleCounterOffset));
            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AtomicReadAndWriteNode atomicIncrement = graph.add(new AtomicReadAndWriteNode(null, sampleCounterOffsetNode, oneConstantNode, JavaKind.Int, LocationIdentity.any()));
            // atomicIncrement.setStateAfter(frameState);
            graph.addAfterFixed(predecessor, atomicIncrement);

            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1000), StampFactory.forKind(JavaKind.Int)));
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(atomicIncrement, sampleRateNode));

            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            ForeignCallNode endTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addAfterFixed(instrumentationBegin, endTime);

            // Atomic read and increment of buffer pointer
            ResolvedJavaField bufferPointerField = context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("bufferIndex"));
            long bufferPointerOffset = bufferPointerField.getOffset();
            ValueNode bufferPointerOffsetNode = graph.addWithoutUnique(ConstantNode.forLong(bufferPointerOffset));
            AtomicReadAndWriteNode atomicPointerIncrement = graph.add(new AtomicReadAndWriteNode(null, bufferPointerOffsetNode, oneConstantNode, JavaKind.Int, LocationIdentity.any()));
            // atomicPointerIncrement.setStateAfter(frameState);
            graph.addAfterFixed(endTime, atomicPointerIncrement);

            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("Buffer"))));
            graph.addAfterFixed(atomicPointerIncrement, readBuffer);

            StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, atomicPointerIncrement, null, null, JavaKind.Long, idNode));
            graph.addAfterFixed(readBuffer, writeToBufferID);

            AddNode pointerIncrement = graph.addWithoutUnique(new AddNode(atomicPointerIncrement, oneConstantNode));
            StoreIndexedNode writeEndTime = graph.add(new StoreIndexedNode(readBuffer, pointerIncrement, null, null, JavaKind.Long, endTime));
            graph.addAfterFixed(writeToBufferID, writeEndTime);

            ValueNode zeroConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(0), StampFactory.forKind(JavaKind.Int)));
            StoreFieldNode resetCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getDeclaredField("sampleCounter")), zeroConstantNode));
            graph.addAfterFixed(writeEndTime, resetCounter);

            atomicIncrement.setNext(ifNode);
            merge.setNext(returnNode);
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation of return node failed: " + e.getMessage(), e);
        }
    }
}