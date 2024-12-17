# Use same paths as eflect.sh
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
DEPS_CP="$DEPS_DIR/dacapo.jar:$DEPS_DIR/async-profiler.jar"

mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
   -J-Djava.library.path=/workspace/graal/vincent vm \
   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
   -javaagent:../joonhwan/agent-joon.jar=method1,method2,method3 \
   -cp "$DEPS_CP" \
   Harness sunflow -n 1 \
   --no-validation
