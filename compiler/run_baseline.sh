#!/bin/bash
# MEASURES BASELINE ENERGY FOR EACH BENCHMARK
# ENERGY IS MEASURED USING RAPL readings accessed via powercap

DEPS_DIR=/workspace/graal_vincent/dacapo
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

DEPS_CP="$DEPS_DIR/dacapo-23.11-MR2-chopin.jar:$CALLBACK_DIR/energy-callback.jar"

##TODO look into avrora
#BENCHMARKS=( "sunflow" "batik" "eclipse" "fop" "h2" "jython" "luindex" "lusearch" "pmd" "tomcat" "xalan" )
BENCHMARKS=( "sunflow" "batik")
# # of iterations per benchmark
ITERATIONS=5

# Loop over each benchmark and run the command
for bm in "${BENCHMARKS[@]}"; do
    echo "Running benchmark: $bm"
    
    mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
       -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
       vm \
       -Dgraal.EnableCustomIRProfiler=false \
       -cp "$DEPS_CP" \
       Harness \
       -c joonhwan.dacapo_callback.EnergyCallback \
       "$bm" -n $ITERATIONS
done
