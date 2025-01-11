
#!/bin/bash

# Set paths
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar

# Simplify classpath to match the working example
EFLECT_CP="$EFLECT_EXPERIMENTS/eflect-experiments.jar:$EFLECT_HOME/eflect.jar"
DEPS_CP="$DEPS_DIR/dacapo.jar:$DEPS_DIR/async-profiler.jar"

# Create output directory
mkdir -p /workspace/graal_vincent/compiler/eflect-output

TOP_METHODS=org.sunflow.core.Ray.transform

mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
   -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
   vm \
   -Dgraal.EnableDVFS=true \
   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
   -cp "$EFLECT_CP:$DEPS_CP" \
   -javaagent:../joonhwan/agent-joon.jar=$TOP_METHODS \
   -Deflect.output=/workspace/graal_vincent/compiler/eflect-output \
   -XX:+UnlockDiagnosticVMOptions \
   -XX:+DebugNonSafepoints \
   Harness \
   sunflow \
   -n 5 \
   -c eflect.experiments.ChappieEflectCallback \
   --no-validation
