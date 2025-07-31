#!/bin/bash
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS="$EFLECT_HOME/experiments"
DACAPO_DIR="/workspace/graal_vincent/dacapo"
DEPS_DIR="$EFLECT_EXPERIMENTS/resources/jar"

EFLECT_CP="$EFLECT_EXPERIMENTS/eflect-experiments.jar:$EFLECT_HOME/eflect.jar"
DEPS_CP="$DACAPO_DIR/dacapo-23.11-MR2-chopin.jar:$DEPS_DIR/async-profiler.jar"

benchmarks=(
  avrora
  batik
  eclipse
  fop
  h2
  jython
  luindex
#  lusearch
  pmd
  sunflow
#  tomcat
#  xalan
)

RUNS=5

ITERATIONS=20

for bench in "${benchmarks[@]}"; do
  for run in $(seq 1 $RUNS); do

    mkdir -p "/workspace/graal_vincent/compiler/eflect-output/$bench/run$run"

    mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
      -J-Djava.library.path="/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin" \
      -J-XX:+DebugNonSafepoints \
      -J-XX:+UnlockDiagnosticVMOptions \
      vm \
      --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
      -cp "$EFLECT_CP:$DEPS_CP" \
      -Deflect.output="/workspace/graal_vincent/compiler/eflect-output/$bench/run$run" \
      Harness "$bench" \
      -c eflect.experiments.ChappieEflectCallback \
      -n $ITERATIONS \
      --no-validation

    echo "Finished benchmark: $bench (Run $run of $RUNS)"
    echo
  done
done

echo "All benchmarks completed."
