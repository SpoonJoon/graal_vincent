package jdk.graal.compiler.phases.common;

import java.util.Optional;
import java.util.Arrays;
import java.util.List;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.DVFS_TEST;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS;
import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.*;
import jdk.graal.compiler.nodes.calc.*;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

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

        try{
            //COMPILATION ID
            Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode ID = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));
            
            ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
            graph.addAfterFixed(graph.start(), dvfsTest);

            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            graph.addAfterFixed(dvfsTest, readBuffer);
            //Read Pointer of Buffer index
            LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex"))));
            graph.addAfterFixed(readBuffer, readPointer);
            //Write to Buffer
            StoreIndexedNode writeDvfsResult = graph.add(new StoreIndexedNode(readBuffer, readPointer, null, null, JavaKind.Long, dvfsTest));
            graph.addAfterFixed(readPointer, writeDvfsResult);

            //increment ptr
            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AddNode incrementPointer = graph.addWithoutUnique(new AddNode(readPointer, oneConstantNode));

            StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex")), incrementPointer));
            graph.addAfterFixed(writeDvfsResult, writePointerBack);



            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                ForeignCallNode javaCurrentCPUtime = graph.add(new ForeignCallNode(DVFS_TEST));
                graph.addBeforeFixed(returnNode, javaCurrentCPUtime);       
            }
        } catch (Throwable e){
            throw new RuntimeException("Instrumentation failed" + e.getMessage(), e);
        }
    }
}
