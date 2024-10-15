package jdk.graal.compiler.phases.common;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.*;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.hotspot.meta.joonhwan.MethodCallTargetBuffer;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodCallTargetSamplingPhase extends BasePhase<HighTierContext> {
    

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
        "xalan",      // Xalan
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

    public MethodCallTargetSamplingPhase() {
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (!shouldInstrument(graph)) {
            return;
        }

        try {
            // Extract a unique identifier for the compilation
            Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode idNode = ConstantNode.forLong(id, graph);

            // Get the ResolvedJavaMethod for sampleTime(long ID)
            Method sampleTimeReflect = MethodCallTargetBuffer.class.getDeclaredMethod("sampleTime", long.class);
            ResolvedJavaMethod sampleTimeMethod = context.getMetaAccess().lookupJavaMethod(sampleTimeReflect);

            // Create a MethodCallTargetNode for the method call
            MethodCallTargetNode methodCallTarget = graph.add(new MethodCallTargetNode(
                    InvokeKind.Static,
                    sampleTimeMethod,
                    new ValueNode[]{idNode},
                    StampPair.createSingle(StampFactory.forVoid()),
                    null  // JavaTypeProfile
            ));
   

            // Create an InvokeNode and insert it after the start node
            InvokeNode invokeNode = graph.add(new InvokeNode(methodCallTarget, BytecodeFrame.UNKNOWN_BCI));
            FixedNode startNext = graph.start().next();
            graph.start().setNext(invokeNode);
            invokeNode.setNext(startNext);

            // Instrument return nodes
            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                instrumentReturnNode(graph, context, returnNode, idNode, sampleTimeMethod);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Determines whether the given graph should be instrumented.
     *
     * @param graph the structured graph representing the method
     * @return {@code true} if the method should be instrumented, {@code false} otherwise
     */
    private boolean shouldInstrument(StructuredGraph graph) {
        String className = graph.method().getDeclaringClass().getName().replace('/', '.').toLowerCase();
        for (String benchmark : BENCHMARK_NAMES) {
            if (className.contains(benchmark.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Instruments a return node by adding a call to sampleTime(long ID).
     *
     * @param graph            the structured graph
     * @param context          the high tier context
     * @param returnNode       the return node to instrument
     * @param idNode           the unique identifier node
     * @param sampleTimeMethod the ResolvedJavaMethod for sampleTime
     */
    private void instrumentReturnNode(StructuredGraph graph, HighTierContext context, ReturnNode returnNode, ValueNode idNode, ResolvedJavaMethod sampleTimeMethod) {
        try {
            // Create a MethodCallTargetNode
            MethodCallTargetNode methodCallTarget = graph.add(new MethodCallTargetNode(
                    InvokeKind.Static,
                    sampleTimeMethod,
                    new ValueNode[]{idNode},
                    StampPair.createSingle(StampFactory.forVoid()),
                    null  // JavaTypeProfile
            ));

            // Create an InvokeNode and insert it before the return node
            InvokeNode invokeNode = graph.add(new InvokeNode(methodCallTarget,BytecodeFrame.UNKNOWN_BCI));

            // Insert the invokeNode before the returnNode
            FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
            predecessor.setNext(invokeNode);
            invokeNode.setNext(returnNode);
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation of return node failed: " + e.getMessage(), e);
        }
    }
}