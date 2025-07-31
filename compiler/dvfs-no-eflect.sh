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

TOP_METHODS=org.sunflow.core.accel.BoundingIntervalHierarchy.intersect
#TOP_METHODS=org.apache.fop.fo.properties.PropertyMaker.findProperty


mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
   -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
   vm \
   -Dgraal.DVFSFrequency=2000000 -Dgraal.EnableDVFSCounterSampling=true -Dgraal.SampleRate=50000\
   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
      -javaagent:../joonhwan/agent-joon.jar=$TOP_METHODS \
   -jar ../dacapo/dacapo-9.12-bach.jar \
   sunflow -n 10  --no-validation

