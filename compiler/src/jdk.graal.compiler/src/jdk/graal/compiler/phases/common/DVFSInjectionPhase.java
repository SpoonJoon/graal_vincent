package jdk.graal.compiler.phases.common;

import java.util.Optional;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.type.StampFactory;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.SCALE_CPU_FREQ;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.code.BytecodeFrame;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;

public class DVFSInjectionPhase extends BasePhase<HighTierContext> {
    private final int sampleRate;

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    public DVFSInjectionPhase(int sampleRate) {
        this.sampleRate=sampleRate;
    }

    private boolean shouldInstrumentDVFS(StructuredGraph graph) {
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
        // Save original start next
        FixedNode originalStartNext = graph.start().next();
        GraphUtil.unlinkFixedNode(graph.start());
        
        ValueNode scalingFreq = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(sampleRate), StampFactory.forKind(JavaKind.Long)));
        ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
        dvfsTest.setStateAfter(graph.start().stateAfter());
        // dvfsTest.setGuard(graph.start().guardAnchor());
        graph.addAfterFixed(graph.start(), dvfsTest);
        // graph.start().setNext(dvfsTest);

        dvfsTest.setNext(originalStartNext);

        // ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
        // graph.addAfterFixed(graph.start(), dvfsTest);

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            ForeignCallNode dvfsTestRet = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
            Bytecode code = new ResolvedJavaMethodBytecode(graph.method());
            FrameState stateAfter = new FrameState(null, code, BytecodeFrame.AFTER_BCI, ValueNode.EMPTY_ARRAY, ValueNode.EMPTY_ARRAY, 0, null, null, ValueNode.EMPTY_ARRAY, null,
                                        FrameState.StackState.BeforePop);
            dvfsTestRet.setStateAfter(graph.add(stateAfter));
            // dvfsTestRet.setStateAfter(returnNode.stateAfter());
            // dvfsTestRet.setGuard(returnNode.guardAnchor());

            graph.addBeforeFixed(returnNode, dvfsTestRet);       
        }
    }
}
