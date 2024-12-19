#! bin/bash
# # mx vm -Dgraal.Dump=:1 FibTest
# mx -J-Djava.library.path=/workspace/graal/vincent vm \
#     -Dgraal.Dump=:1 \
#     --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
#     -javaagent:../joonhwan/agent-joon.jar \
#     -jar ../dacapo/dacapo-9.12-bach.jar sunflow

## Run using an agent that initializes the buffer

mx -J-Djava.library.path=/workspace/graal/vincent --java-home /openjdk-21/build/linux-x86_64-server-release/images/jdk vm -Dgraal.EnableDVFS=true -Dgraal.SampleRate=1000 \
    -Xmx10g \
    --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
    -javaagent:../joonhwan/agent-joon.jar \
    -jar ../dacapo/dacapo-9.12-bach.jar -n 1 sunflow


# mx -J-Djava.library.path=/workspace/graal/vincent vm \
#     -XX:+PrintCompilation \
#     -Xmx10g \
#     -Dgraal.Dump=:1 \
#     --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
#     -javaagent:../joonhwan/agent-joon.jar \
#     FibTest


# mx -J-Djava.library.path=/workspace/graal/vincent vm \
#     -Xmx10g \
#     --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
#     -javaagent:../joonhwan/agent-joon.jar \
#     FibTest

# mx vm -Xmx10g  \
#  -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI \
#   -Dgraal.CompilationFailureAction="Print" \
#   -XX:+UseJVMCICompiler  \
#   jdk.internal.vm.compiler/org.graalvm.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
#   FibTestd
