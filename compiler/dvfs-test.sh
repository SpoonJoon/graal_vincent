#!/bin/bash

# Set paths
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"

BENCHMARK=sunflow
ITERATIONS=10
TARGET_METHOD=org.sunflow.core.Ray.transform

    
mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
   -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
   vm \
   -Dgraal.EnableDVFSCounterSampling=true -Dgraal.SampleRate=1000\
   -javaagent:../joonhwan/agent-joon.jar=$TARGET_METHOD \
   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
   -cp "$DEPS_CP" \
   Harness \
   -c joonhwan.dacapo_callback.EnergyCallback \
   "$BENCHMARK" -n $ITERATIONS


