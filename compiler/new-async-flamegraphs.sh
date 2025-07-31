#!/bin/bash
set -e

OUTPUT_DIR="$(pwd)/dacapo-flamegraphs"
PROFILER_DIR="$(pwd)/async-profiler"
JAVA_HOME="/openjdk-21/build/linux-x86_64-server-release/images/jdk"
DACAPO_DIR="/workspace/graal_vincent/dacapo"
JAVA_LIB_PATH="$JAVA_HOME/lib:$JAVA_HOME/lib/server"

mkdir -p "$OUTPUT_DIR" "$PROFILER_DIR"

if [ ! -f "$PROFILER_DIR/lib/libasyncProfiler.so" ]; then
    wget -O "$PROFILER_DIR/async-profiler.tar.gz" https://github.com/async-profiler/async-profiler/releases/download/v4.0/async-profiler-4.0-linux-x64.tar.gz
    tar -xzf "$PROFILER_DIR/async-profiler.tar.gz" -C "$PROFILER_DIR" --strip-components=1
fi

ASYNC_PROFILER_LIB="$PROFILER_DIR/lib/libasyncProfiler.so"

unset JAVA_TOOL_OPTIONS

benchmarks=(
  avrora
  batik
  fop
  h2
  jython
  luindex
  pmd
  sunflow
)

ITERATIONS=30

chmod +x "$ASYNC_PROFILER_LIB"

for bench in "${benchmarks[@]}"; do
    mkdir -p "$OUTPUT_DIR/$bench"

    # CPU profiling
    mx --java-home=$JAVA_HOME \
      -J-Djava.library.path="$JAVA_LIB_PATH" \
      -J-XX:+DebugNonSafepoints \
      -J-XX:+UnlockDiagnosticVMOptions \
      vm \
      --add-opens java.base/java.lang=ALL-UNNAMED \
      --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
      -agentpath:$ASYNC_PROFILER_LIB=start,event=cpu,file="$OUTPUT_DIR/$bench/${bench}_cpu.html",interval=10ms,title="CPU Profiling - $bench" \
      -jar "$DACAPO_DIR/dacapo-23.11-MR2-chopin.jar" \
      $bench -n $ITERATIONS

done
