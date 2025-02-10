#!/bin/bash
# run-all-methods.sh

EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"

AVAILABLE_FREQS=(2201000 2200000 2100000 2000000 1900000 1800000 1700000 1600000 1500000 1400000 1300000 1200000)
DEFAULT_SAMPLING_RATES=(1 10 100 1000 10000)
SUNFLOW_SAMPLING_RATES=(100 1000 10000 100000 500000)
ITERATIONS=20
# number of "cold runs" coined by David Liu 
RUNS=10 

OUTPUT_DIR="/workspace/graal_vincent/compiler/dvfs-output"
mkdir -p "$OUTPUT_DIR"

# TODO parametrize thiss
declare -A BENCHMARK_METHODS
#BENCHMARK_METHODS["eclipse"]="org.eclipse.jdt.internal.core.index.DiskIndex.addQueryResults,org.eclipse.jdt.internal.core.JavaModelManager.getZipFile,org.eclipse.jdt.internal.core.util.SimpleWordSet.add,org.eclipse.jdt.internal.core.index.DiskIndex.writeStreamChars,org.eclipse.jdt.internal.compiler.parser.Scanner.getNextToken0"
BENCHMARK_METHODS["fop"]="sun.nio.cs.StreamEncoder.writeBytes,org.apache.fop.fonts.FontInfo.getFontInstance,sun.nio.ch.UnixFileDispatcherImpl.read0,org.apache.fop.fo.properties.PropertyMaker.findProperty,org.apache.fop.fo.CharIterator.clone"
#BENCHMARK_METHODS["h2"]="org.h2.mvstore.db.ValueDataType.compare,org.h2.mvstore.Page.binarySearch,org.h2.mvstore.db.ValueDataType.compareValues,org.h2.mvstore.type.ObjectDataType\$LongType.compare,org.h2.value.DataType.getDataType"
#BENCHMARK_METHODS["jython"]="sun.nio.fs.UnixNativeDispatcher.open0,org.graalvm.collections.EconomicMapImpl.findLinear,org.graalvm.collections.EconomicMapImpl.getHashIndex,org.graalvm.collections.EconomicMapImpl.setValue,org.graalvm.collections.EconomicMapImpl.setRawValue"
#BENCHMARK_METHODS["pmd"]="org.dacapo.harness.TeeOutputStream.write,net.sf.saxon.om.Navigator\$DescendantEnumeration.advance,net.sf.saxon.pattern.NameTest.matches,org.jaxen.expr.DefaultNameStep.evaluate,org.jaxen.expr.DefaultLocationPath.evaluate"
BENCHMARK_METHODS["sunflow"]="org.sunflow.core.accel.KDTree.intersect,org.sunflow.core.primitive.TriangleMesh\$WaldTriangle.intersect,org.sunflow.core.accel.BoundingIntervalHierarchy.intersect,org.sunflow.core.accel.NullAccelerator.intersect,org.sunflow.core.Ray.transform"
#BENCHMARK_METHODS["tomcat"]="org.dacapo.harness.TeeOutputStream.write,org.dacapo.tomcat.Page.writeLog,sun.nio.cs.StreamEncoder.implClose,sun.nio.ch.SocketDispatcher.write0,sun.nio.cs.StreamEncoder.writeBytes"
#BENCHMARK_METHODS["xalan"]="sun.nio.cs.StreamEncoder.writeBytes,sun.nio.cs.StreamEncoder.implWrite,sun.nio.cs.StreamEncoder.write,org.apache.xml.serializer.ToStream.characters,org.apache.xml.dtm.ref.DTMManagerDefault.getDTM"

for benchmark in "${!BENCHMARK_METHODS[@]}"; do
    # Create a CSV file per benchmark.
    CSV_FILE="$OUTPUT_DIR/${benchmark}_results.csv"
    echo "benchmark,target_method,frequency,sample_rate,run,iteration,iteration_type,duration,elapsed,energy" > "$CSV_FILE"

    IFS=',' read -ra methods_array <<< "${BENCHMARK_METHODS[$benchmark]}"
    if [ "$benchmark" == "sunflow" ]; then
        sampling_rates=("${SUNFLOW_SAMPLING_RATES[@]}")
    else
        sampling_rates=("${DEFAULT_SAMPLING_RATES[@]}")
    fi
    for method in "${methods_array[@]}"; do
        TARGET_METHOD="$method"
        for freq in "${AVAILABLE_FREQS[@]}"; do
            for sr in "${sampling_rates[@]}"; do
                echo "---------------------------------------------------------"
                echo "Running benchmark '$benchmark' DVFS TARGET_METHOD='$TARGET_METHOD', frequency=$freq, DVFS sample rate=$sr"
                
                for ((run=1; run<=RUNS; run++)); do
                    echo "  Run $run/$RUNS"
                    tmp_output=$(mktemp)
                    
                    mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
                       -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
                       vm \
                       -Dgraal.DVFSFrequency="$freq" -Dgraal.EnableDVFSCounterSampling=true -Dgraal.SampleRate="$sr" \
                       --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
                       -javaagent:../joonhwan/agent-joon.jar="$TARGET_METHOD" \
                       -cp "$DEPS_CP" \
                       Harness \
                       -c joonhwan.dacapo_callback.EnergyCallback \
                       "$benchmark" -n $ITERATIONS > "$tmp_output" 2>&1

                    iteration_lines=$(grep -E "(Warm-up|Measurement) iteration:" "$tmp_output")
                    if [ -z "$iteration_lines" ]; then
                        echo "    No iteration found for run $run of benchmark '$benchmark'."
                        echo "$benchmark,$TARGET_METHOD,$freq,$sr,$run,NA,NA,NA,NA,NA" >> "$CSV_FILE"
                    else
                        iter_count=0
                        while IFS= read -r iteration_line; do
                            iter_count=$((iter_count + 1))
                            iteration_type=$(echo "$iteration_line" | grep -oP "^(Warm-up|Measurement)")
                            duration=$(echo "$iteration_line" | grep -oP '(?<=Duration = )\d+(?= ms)')
                            elapsed=$(echo "$iteration_line" | grep -oP '(?<=Elapsed = )\d+(?= ns)')
                            energy=$(echo "$iteration_line" | grep -oP '(?<=Energy = )\d+(?= micro joules)')
                            echo "    Iteration $iter_count ($iteration_type): Duration = ${duration} ms, Elapsed = ${elapsed} ns, Energy = ${energy} micro joules"
                            echo "$benchmark,$TARGET_METHOD,$freq,$sr,$run,$iter_count,$iteration_type,$duration,$elapsed,$energy" >> "$CSV_FILE"
                        done <<< "$iteration_lines"
                    fi
                    rm "$tmp_output"
                done
            done
        done
    done
done
