package jdk.graal.compiler.hotspot.meta.joonhwan;

import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.INIT_SYSFS_FILES;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.CLEANUP_SYSFS_FILES;

import java.util.ArrayList;
import java.util.List;


 public class JoonhwanUtils extends Thread {

        public static volatile int bufferIndex = 0;
        public static volatile int sampleCounter = 0;
        public static List<String> methodList = new ArrayList<>();


        public static void initMethodList(List<String> methods) {
                methodList.clear();
                methodList.addAll(methods);
                System.out.println("DVFS Candidate Method List Initialized: " + methodList);
        }

        //TODO add new foreign calls
        public static void initDVFS(){
                
        }

        public static void cleanupDVFS(){}
        
}
