#!/bin/bash
DEPS_DIR=/workspace/graal_vincent/dacapo
CALLBACK_DIR=/workspace/graal_vincent/joonhwan
DEPS_CP="$DEPS_DIR/dacapo-23.11-MR2-chopin.jar:$CALLBACK_DIR/energy-callback.jar"

BENCHMARKS=("sunflow" "avrora")
#BENCHMARKS=( "sunflow" "batik" "eclipse" "fop" "h2" "jython" "luindex" "lusearch" "pmd" "tomcat" "xalan" )
ITERATIONS=20
RUNS=5
OUTPUT_DIR="compilation_logs"
OUTPUT_CSV="compilation_tracker.csv"

# Create output directory if it doesn't exist
mkdir -p $OUTPUT_DIR

for bm in "${BENCHMARKS[@]}"; do
  for run in $(seq 1 $RUNS); do
    iter=0
    # Create log filename with format benchmarkname_iterations_compilation.log
    LOG_FILE="$OUTPUT_DIR/${bm}_${run}_${ITERATIONS}iters_compilation.log"
    
    echo "Running benchmark $bm, saving output to $LOG_FILE"
    
    mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
      -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
      vm \
      -Dgraal.EnableCustomIRProfiler=false \
      -XX:+PrintCompilation \
      --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
      -cp "$DEPS_CP" \
      Harness \
      "$bm" -n $ITERATIONS > "$LOG_FILE" 2>&1
  done
done
