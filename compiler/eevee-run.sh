mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
   -J-Djava.library.path=/workspace/graal/vincent vm \
   -Dgraal.EnableDVFS=false \
   -Dgraal.SampleRate=1000 \
   -Xmx10g \
   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
   -javaagent:../joonhwan/agent-joon.jar=method1,method2,method3 \
   -jar ../dacapo/dacapo-9.12-bach.jar -n 1 sunflow

