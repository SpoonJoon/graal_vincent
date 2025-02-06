#!/bin/bash
# MEASURES BASELINE ENERGY FOR EACH BENCHMARK
# ENERGY IS MEASURED USING RAPL readings accessed via powercap

EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"

##TODO look into avrora
BENCHMARKS=( "sunflow" "batik" "eclipse" "fop" "h2" "jython" "luindex" "lusearch" "pmd" "tomcat" "xalan" )
# # of iterations per benchmark
ITERATIONS=10

# Loop over each benchmark and run the command
for bm in "${BENCHMARKS[@]}"; do
    echo "Running benchmark: $bm"
    
    mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
       -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
       vm \
       -Dgraal.EnableCustomIRProfiler=false \
       --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
       -cp "$DEPS_CP" \
       Harness \
       -c joonhwan.dacapo_callback.EnergyCallback \
       "$bm" -n $ITERATIONS
done
