#!/bin/bash
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"
BENCHMARKS=( "sunflow" "batik" "eclipse" "fop" "h2" "jython" "luindex" "pmd" )
#BENCHMARKS=("sunflow")
ITERATIONS=20
RUNS=5
OUTPUT_CSV="compilation_tracker.csv"
echo "benchmark,run,iteration,energy,iteration_time" > "$OUTPUT_CSV"
for bm in "${BENCHMARKS[@]}"; do
  for run in $(seq 1 $RUNS); do
    iter=0
    output=$(mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
      -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
      vm \
      -Dgraal.EnableCustomIRProfiler=false \
      -XX:+PrintCompilation \
      --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
      -cp "$DEPS_CP" \
      Harness \
      -c joonhwan.dacapo_callback.EnergyCallback \
      "$bm" -n $ITERATIONS)
    while IFS= read -r line; do
      if [[ $line =~ (Warm-up|Measurement)[[:space:]]iteration:\ Duration\ =\ ([0-9]+)\ ms,.*Energy\ =\ ([0-9]+)\ micro\ joules ]]; then
        iter=$((iter+1))
        duration="${BASH_REMATCH[2]}"
        energy="${BASH_REMATCH[3]}"
        echo "$bm,$run,$iter,$energy,$duration" >> "$OUTPUT_CSV"
      fi
    done <<< "$output"
  done
done
echo "Results saved to $OUTPUT_CSV"

