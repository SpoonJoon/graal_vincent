#!/bin/bash
# run-all-methods.sh

CALLBACK_DIR=/workspace/graal_vincent/joonhwan
DACAPO_DIR="/workspace/graal_vincent/dacapo"
# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DACAPO_DIR/dacapo-23.11-MR2-chopin.jar:$CALLBACK_DIR/energy-callback.jar"
AVAILABLE_FREQS=(2201000 2200000 2100000 2000000 1900000 1800000 1700000 1600000 1500000 1400000 1300000 1200000)
#AVAILABLE_FREQS=(2200000 2100000)
ITERATIONS=20
# number of "cold runs" coined by David Liu 
RUNS=3

OUTPUT_DIR="/workspace/graal_vincent/compiler/final-dvfs-output"
mkdir -p "$OUTPUT_DIR"
DVFS_LOG_FILE="/workspace/graal_vincent/compiler/jvm_dvfs.log"

# TODO parametrize this
declare -A BENCHMARK_METHODS
BENCHMARK_METHODS["batik"]="org.apache.batik.ext.awt.image.codec.png.PNGImageEncoder.encodePass,org.apache.batik.parser.NumberParser.parseFloat,org.apache.batik.gvt.FillShapePainter.paint,org.apache.batik.gvt.StrokeShapePainter.getPaintedArea,org.apache.batik.gvt.StrokeShapePainter.paint"
#BENCHMARK_METHODS["eclipse"]="org.eclipse.jdt.core.compiler.CharOperation.hashCode,org.eclipse.jdt.core.Signature.encodeQualifiedName,org.eclipse.jdt.internal.compiler.parser.Parser.parse,org.eclipse.jdt.internal.core.util.SimpleWordSet.add,org.eclipse.jdt.internal.compiler.parser.Scanner.scanIdentifierOrKeyword"
BENCHMARK_METHODS["h2"]="org.h2.mvstore.db.RowDataType.compareSearchRows,org.h2.value.Value.compareToNotNullable,org.h2.mvstore.CursorPos.traverseDown,org.h2.mvstore.db.RowDataType.binarySearch,org.h2.mvstore.tx.Transaction.markStatementStart"
BENCHMARK_METHODS["luindex"]="org.apache.lucene.analysis.standard.StandardTokenizerImpl.getNextToken,org.apache.lucene.util.BytesRefHash.equals,org.apache.lucene.util.BytesRefHash.doHash,org.apache.lucene.analysis.CharacterUtils.toLowerCase,org.dacapo.luindex.Index.indexLineDoc"
BENCHMARK_METHODS["sunflow"]="org.sunflow.core.light.TriangleMeshLight.getRadiance,org.sunflow.core.accel.KDTree.intersectPrimitive,org.sunflow.core.primitive.TriangleMesh$WaldTriangle.intersect,org.sunflow.core.accel.BoundingIntervalHierarchy.intersect,org.sunflow.core.gi.InstantGI.getIrradiance,org.sunflow.core.Geometry.intersect"
BENCHMARK_METHODS["avrora"]="sun.nio.cs.StreamEncoder.writeBytes,avrora.sim.clock.RippleSynchronizer.waitForLink,avrora.arch.legacy.LegacyInterpreter.fastLoop,avrora.sim.AtmelInterpreter.commit,avrora.arch.legacy.LegacyInterpreter.visit"
BENCHMARK_METHODS["jython"]="sun.security.provider.SHA.implCompress0,org.python.modules.gc.notifyPostFinalization,org.python.modules.gc.getJythonGCFlags,sun.security.provider.DigestBase.engineDigest,sun.nio.fs.UnixPath.normalizeAndCheck"
BENCHMARK_METHODS["pmd"]="net.sf.saxon.om.Navigator$AxisFilter.next,net.sf.saxon.om.StructuredQName.equals,net.sf.saxon.om.NamePool.allocateInternal,net.sf.saxon.om.Navigator$BaseEnumeration.next,net.sf.saxon.sxpath.XPathDynamicContext.setVariable"


## MANUALLY selected methods
#BENCHMARK_METHODS["avrora"]="avrora.arch.legacy.LegacyInterpreter.fastLoop,avrora.arch.legacy.LegacyInterpreter.sleepLoop,avrora.sim.radio.Medium.Receiver.Ticker.fire,avrora.sim.radio.Medium.Receiver.Ticker.fireLocked,avrora.sim.radio.Medium.Receiver.Ticker.fireUnlocked"

TIMEOUT_DURATION=600

for benchmark in "${!BENCHMARK_METHODS[@]}"; do
    CSV_FILE="$OUTPUT_DIR/${benchmark}_results.csv"
    echo "benchmark,target_method,frequency,run,iteration,iteration_type,duration,elapsed,energy" > "$CSV_FILE"

    DVFS_CSV_FILE="$OUTPUT_DIR/${benchmark}_dvfs_counts.csv"
    echo "benchmark,target_method,frequency,run,scaling_count,restore_count" > "$DVFS_CSV_FILE"

    IFS_BAK=$IFS
    IFS=',' read -ra methods_array <<< "${BENCHMARK_METHODS[$benchmark]}"
    IFS=$IFS_BAK

    BENCHMARK_LOG_FILE="$OUTPUT_DIR/${benchmark}_benchmark.log"
 	
    for method in "${methods_array[@]}"; do
        TARGET_METHOD="$method"
        local_iters=$ITERATIONS
        [ "$benchmark" = "sunflow" ] && local_iters=30

        for freq in "${AVAILABLE_FREQS[@]}"; do
            echo "---------------------------------------------------------" >> "$BENCHMARK_LOG_FILE"
            echo "Running benchmark '$benchmark' DVFS TARGET_METHOD='$TARGET_METHOD', frequency=$freq" >> "$DVFS_LOG_FILE"

            for ((run=1; run<=RUNS; run++)); do
                ./restore-governors.sh
		echo "  Run $run/$RUNS" >> "$BENCHMARK_LOG_FILE"
                tmp_output=$(mktemp)

                # Using the original JVM options
                timeout ${TIMEOUT_DURATION}s mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
                   vm \
                   -Dgraal.DVFSFrequency="$freq" -Dgraal.EnableDVFS=true \
                   --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
                   -javaagent:../joonhwan/agent-joon.jar="$TARGET_METHOD" \
                   -cp "$DEPS_CP" \
                   Harness \
		   -c joonhwan.dacapo_callback.EnergyCallback \
		   "$benchmark" -n "$local_iters" --no-validation > "$tmp_output" 2>&1

                exit_status=$?
                cat "$tmp_output" >> "$BENCHMARK_LOG_FILE"
		
		# epilogue and prologue counter parsing
		dvfs_lines=$(grep "JOONHWAN: \[DVFS\] Scaling Count:" "$DVFS_LOG_FILE" | tail -5)
                relevant_line=$(echo "$dvfs_lines" | grep -v "Scaling Count: 0" | tail -1)
                
                if [ -n "$relevant_line" ]; then
                    scaling_count=$(echo "$relevant_line" | grep -oP '(?<=Scaling Count: )\d+')
                    restore_count=$(echo "$relevant_line" | grep -oP '(?<=restore count: )\d+')
                    echo "$benchmark,$TARGET_METHOD,$freq,$run,$scaling_count,$restore_count" >> "$DVFS_CSV_FILE"
                    echo "    DVFS: Scaling Count=$scaling_count, Restore Count=$restore_count" >> "$BENCHMARK_LOG_FILE"
                else
                    echo "$benchmark,$TARGET_METHOD,$freq,$run,0,0" >> "$DVFS_CSV_FILE"
                    echo "    DVFS: No scaling data found" >> "$BENCHMARK_LOG_FILE"
                fi

                # Explicit timeout handling
                if [ $exit_status -eq 124 ]; then
                    echo "    TIMEOUT after ${TIMEOUT_DURATION}s" >> "$BENCHMARK_LOG_FILE"
                    echo "$benchmark,$TARGET_METHOD,$freq,$run,TIMEOUT,TIMEOUT,${TIMEOUT_DURATION}000,NA,NA" >> "$CSV_FILE"
                    rm -f "$tmp_output"
                    break 2 
                fi

                iteration_lines=$(grep -E "(Warm-up|Measurement) iteration:" "$tmp_output")
                if [ -z "$iteration_lines" ]; then
                    echo "    No iteration found for run $run of benchmark '$benchmark'." >> "$BENCHMARK_LOG_FILE"
                    echo "$benchmark,$TARGET_METHOD,$freq,$run,NA,NA,NA,NA,NA" >> "$CSV_FILE"
                else
                    # Process each iteration line
                    iter_count=0
                    while IFS= read -r line; do
                        iter_count=$((iter_count + 1))
                        iteration_type=$(echo "$line" | grep -oP "^(Warm-up|Measurement)")
                        duration=$(echo "$line" | grep -oP '(?<=Duration = )\d+(?= ms)')
                        elapsed=$(echo "$line" | grep -oP '(?<=Elapsed = )\d+(?= ns)')
                        energy=$(echo "$line" | grep -oP '(?<=Energy = )\d+(?= micro joules)')

                        echo "    Iteration $iter_count ($iteration_type): Duration=$duration ms, Elapsed=$elapsed ns, Energy=$energy μJ" >> "$BENCHMARK_LOG_FILE"
                        echo "$benchmark,$TARGET_METHOD,$freq,$run,$iter_count,$iteration_type,$duration,$elapsed,$energy" >> "$CSV_FILE"
                    done <<< "$iteration_lines"
                fi
                rm -f "$tmp_output"
            done
        done
    done
done
