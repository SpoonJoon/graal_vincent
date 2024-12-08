package jdk.graal.compiler.phases.common;

import java.util.Optional;
import java.util.Arrays;
import java.util.List;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.DVFS_TEST;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS;
import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.*;
import jdk.graal.compiler.nodes.calc.*;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;


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

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {

        if (!shouldInstrument(graph)) {
            return;
        }
        // Save original start next
        FixedNode originalStartNext = graph.start().next();
        // unlink graph.start() -> originalStartNext 
        GraphUtil.unlinkFixedNode(graph.start());
        ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
        graph.start().setNext(dvfsTest);
        dvfsTest.setNext(originalStartNext);

        // ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
        // graph.addAfterFixed(graph.start(), dvfsTest);

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            ForeignCallNode dvfsTestRet = graph.add(new ForeignCallNode(DVFS_TEST));
            graph.addBeforeFixed(returnNode, dvfsTestRet);       
        }
    }
}
