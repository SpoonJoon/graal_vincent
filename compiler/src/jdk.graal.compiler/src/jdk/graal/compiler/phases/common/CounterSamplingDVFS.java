package jdk.graal.compiler.phases.common;

import java.util.Arrays;
import java.util.List;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.SCALE_CPU_FREQ;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS;

import java.util.Optional;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;

public class CounterSamplingDVFS extends BasePhase<HighTierContext> {
    private final int sampleRate;
    private final int DVFSFreq;
 
    // Define the list of benchmark identifiers
    private static final List<String> BENCHMARK_NAMES = Arrays.asList(
        "sunflow",    // Sunflow
        "batik",      // Batik
        "derby",      // Derby
        "eclipse",    // Eclipse
        "fop",        // FOP
        "jfree",      // JFree
        "menalto",    // Menalto
        "sablecc",    // SableCC
        "xalan",       // Xalan
        "pmd"
        // Add other DaCapo benchmark names here
    );

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    public CounterSamplingDVFS(int sampleRate, int cpufreq) {
        this.sampleRate=sampleRate;
        this.DVFSFreq = cpufreq;
    }

    private boolean shouldInstrumentDVFS(StructuredGraph graph) {
        //This is loaded by a java agent
        String targetMethod = BuboCache.methodList.get(0);
        String[] targetParts = targetMethod.split("\\.");
        
        // Normalize and compare class and method names
        String targetClassName = targetMethod.substring(0, targetMethod.lastIndexOf('.')).toLowerCase();
        String currentClassName = graph.method().getDeclaringClass().getName()
            .replace('/', '.')
            .toLowerCase()
            .replaceAll("^l", "")
            .replaceAll(";$", "");  // Remove trailing semicolon
    
        String targetMethodName = targetParts[targetParts.length - 1];
            
        if (currentClassName.equals(targetClassName) && 
            graph.method().getName().equals(targetMethodName)) {
            System.out.println("Found target method: " + targetMethod);
            return true;
        }
        
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (!shouldInstrumentDVFS(graph)) {
            return;
        }

        try {
            // Extract a unique identifier for the compilation
            Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode idNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));

            // make endnodes
            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            // Create BeginNodes for instrumentation and skip paths
            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            // Save start.next
            FixedNode ogStartNext = graph.start().next();

            LoadFieldNode loadSampleCounter = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter"))));
            graph.addAfterFixed(graph.start(), loadSampleCounter);

            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));

            // Define the sampling rate (e.g., every 100 calls)
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(sampleRate), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(loadSampleCounter, sampleRateNode));

            // Create IfNode with the sampling condition
            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Create Merges
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);
            
            // scaling frequency TODO parametrize this
            ValueNode scalingFreq = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1600000), StampFactory.forKind(JavaKind.Int)));
            // Connect instrumentationBegin to startTime
            ForeignCallNode scaleCPUFreq = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
            graph.addAfterFixed(instrumentationBegin, scaleCPUFreq);

            // SKIP instrumentation LOGIC
            AddNode incSampleCount = graph.addWithoutUnique(new AddNode(loadSampleCounter, oneConstantNode));
            StoreFieldNode writeIncCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incSampleCount));
            graph.addAfterFixed(skipInstrumentationBegin, writeIncCounter);

            //After the Load the sample counter -> ifNode
            loadSampleCounter.setNext(ifNode);
            merge.setNext(ogStartNext);

            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                instrumentReturnNode(graph, context, returnNode, idNode);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation failed: " + e.getMessage(), e);
        }
    }

    private void instrumentReturnNode(StructuredGraph graph, HighTierContext context, ReturnNode returnNode, ValueNode idNode) {
        try {
            FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
            if (predecessor == null) {
                throw new RuntimeException("Return node has no predecessor");
            }

            // make endnodes
            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            // Create BeginNodes for instrumentation and skip paths
            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            //Load sampleCounter
            LoadFieldNode loadSampleCounter = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter"))));
            graph.addAfterFixed(predecessor, loadSampleCounter);


            // ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            // Move sampleCounter increment to start
            // AddNode incSampleCount = graph.addWithoutUnique(new AddNode(loadSampleCounter, oneConstantNode));
            // StoreFieldNode writeIncCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incSampleCount));
            // graph.addAfterFixed(loadSampleCounter, writeIncCounter);
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(this.sampleRate), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(loadSampleCounter, sampleRateNode));

            // Create new branches based on the sampling condition
            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Create Merges
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            ValueNode scalingFreq = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1600000), StampFactory.forKind(JavaKind.Int)));
            // Connect instrumentationBegin to startTime
            ForeignCallNode endTime = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
            graph.addAfterFixed(instrumentationBegin, endTime);
            //Read Buffer to write ID, startTime, and endTime
            // LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            // graph.addAfterFixed(endTime, readBuffer);
            // //Read Pointer of Buffer index
            // LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex"))));
            // graph.addAfterFixed(readBuffer, readPointer);
            // //write compilationID to buffer
            // StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, readPointer, null, null, JavaKind.Long, idNode));
            // graph.addAfterFixed(readPointer, writeToBufferID);
            // //increment ptr
            // AddNode pointerIncrement1 = graph.addWithoutUnique(new AddNode(readPointer, oneConstantNode));
            // StoreIndexedNode writeEndTime = graph.add(new StoreIndexedNode(readBuffer, pointerIncrement1, null, null, JavaKind.Long, endTime));
            // graph.addAfterFixed(readPointer, writeEndTime);
            // // Store incremented the pointer
            // AddNode pointerIncrement2 = graph.addWithoutUnique(new AddNode(pointerIncrement1, oneConstantNode));
            // StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex")), pointerIncrement2));
            // graph.addAfterFixed(writeEndTime, writePointerBack);
            //reset counter
            ValueNode zeroConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(0), StampFactory.forKind(JavaKind.Int)));
            StoreFieldNode resetCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), zeroConstantNode));
            graph.addAfterFixed(endTime, resetCounter);

            // =========================
            // Merge and Continue
            // =========================
            // writeIncCounter.setNext(ifNode); //moved sampling logic upwards
            loadSampleCounter.setNext(ifNode);
            merge.setNext(returnNode);
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation of return node failed: " + e.getMessage(), e);
        }
    }
}