#!/bin/bash

# Set paths
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"

ITERATIONS=20

# Define benchmark methods for each benchmark
declare -A BENCHMARK_METHODS
BENCHMARK_METHODS["fop"]="sun.nio.cs.StreamEncoder.writeBytes,org.apache.fop.fonts.FontInfo.getFontInstance,sun.nio.ch.UnixFileDispatcherImpl.read0,org.apache.fop.fo.properties.PropertyMaker.findProperty,org.apache.fop.fo.CharIterator.clone"
BENCHMARK_METHODS["sunflow"]="org.sunflow.core.accel.KDTree.intersect,org.sunflow.core.primitive.TriangleMesh\$WaldTriangle.intersect,org.sunflow.core.accel.BoundingIntervalHierarchy.intersect,org.sunflow.core.accel.NullAccelerator.intersect,org.sunflow.core.Ray.transform"

# Loop over benchmarks
for BENCHMARK in "fop" "sunflow"; do
    # Split the methods by comma into an array
    IFS=',' read -ra METHODS <<< "${BENCHMARK_METHODS[$BENCHMARK]}"
    for TARGET_METHOD in "${METHODS[@]}"; do
        echo "Running benchmark $BENCHMARK with method $TARGET_METHOD"
        mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
           -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
           vm \
           -Dgraal.EnableDVFSCounterSampling=true -Dgraal.SampleRate=100000 \
           -javaagent:../joonhwan/agent-joon.jar=$TARGET_METHOD \
           --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
           -cp "$DEPS_CP" \
           Harness \
           -c joonhwan.dacapo_callback.EnergyCallback \
           "$BENCHMARK" -n $ITERATIONS
    done
done

