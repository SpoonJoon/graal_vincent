package jdk.graal.compiler.phases.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.DVFS_TEST;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.SCALE_CPU_FREQ;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;


public class DVFSInjectionPhase extends BasePhase<HighTierContext> {

    //TODO: make this an argument..?
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

    public DVFSInjectionPhase() {
    }

    private boolean shouldInstrument(StructuredGraph graph) {
        String className = graph.method().getDeclaringClass().getName().replace('/', '.').toLowerCase();
        for (String benchmark : BENCHMARK_NAMES) {
            if (className.contains(benchmark.toLowerCase())) {
                return true;
            }
        }
        return false;
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
        // unlink graph.start() -> originalStartNext 
        GraphUtil.unlinkFixedNode(graph.start());

        ValueNode scalingFreq = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(2200000000L), StampFactory.forKind(JavaKind.Long)));

        ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
        graph.start().setNext(dvfsTest);
        dvfsTest.setNext(originalStartNext);

        // ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
        // graph.addAfterFixed(graph.start(), dvfsTest);

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            ForeignCallNode dvfsTestRet = graph.add(new ForeignCallNode(SCALE_CPU_FREQ, scalingFreq));
            graph.addBeforeFixed(returnNode, dvfsTestRet);       
        }
    }
}
