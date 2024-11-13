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

        try{
            // Save original start next
            FixedNode originalStartNext = graph.start().next();
            // unlink graph.start() -> originalStartNext 
            GraphUtil.unlinkFixedNode(graph.start());

            // Create an instrumentation block
            BeginNode instrumentationBegin = graph.add(new BeginNode());

            // Create an end node for the instrumentation block
            EndNode instrumentationEnd = graph.add(new EndNode());
            instrumentationBegin.setNext(instrumentationEnd);

            // Merge back into the main control flow
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.setNext(originalStartNext);

            // start -> instrumentaiton begin -> instrumentation end
            graph.start().setNext(instrumentationBegin);
          
            // Create the nodes
            ForeignCallNode dvfsTest = graph.add(new ForeignCallNode(DVFS_TEST));
            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null,
                context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null,
                context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex"))));
            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            StoreIndexedNode writeDvfsResult = graph.add(new StoreIndexedNode(readBuffer, readPointer, null, null, JavaKind.Long, dvfsTest));
            AddNode incrementPointer = graph.addWithoutUnique(new AddNode(readPointer, oneConstantNode));
            StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null,
                context.getMetaAccess().lookupJavaField(BuboCache.class.getField("bufferIndex")), incrementPointer));

            // Link the nodes within the instrumentation block
            instrumentationBegin.setNext(dvfsTest);
            dvfsTest.setNext(readBuffer);
            readBuffer.setNext(readPointer);
            readPointer.setNext(writeDvfsResult);
            writeDvfsResult.setNext(writePointerBack);
            writePointerBack.setNext(instrumentationEnd);

            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                ForeignCallNode javaCurrentCPUtime = graph.add(new ForeignCallNode(DVFS_TEST));
                graph.addBeforeFixed(returnNode, javaCurrentCPUtime);       
            }
        } catch (Throwable e){
            throw new RuntimeException("Instrumentation failed" + e.getMessage(), e);
        }
    }
}
