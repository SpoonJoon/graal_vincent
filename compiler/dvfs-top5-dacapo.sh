#!/bin/bash
# run-all-methods.sh

EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar
CALLBACK_DIR=/workspace/graal_vincent/joonhwan

# PREREQ: Dacapo from eflect and energy-callback need to be built
DEPS_CP="$DEPS_DIR/dacapo.jar:$CALLBACK_DIR/energy-callback.jar"

AVAILABLE_FREQS=(2201000 2200000 2100000 2000000 1900000 1800000 1700000 1600000 1500000 1400000 1300000 1200000)
ITERATIONS=10

# Create output directory
mkdir -p /workspace/graal_vincent/compiler/eflect-output

# Declare array mapping benchmark names to their TARGET_METHOD lists.
declare -A BENCHMARK_METHODS
BENCHMARK_METHODS["eclipse"]="org.eclipse.jdt.internal.core.index.DiskIndex.addQueryResults,org.eclipse.jdt.internal.core.JavaModelManager.getZipFile,org.eclipse.jdt.internal.core.util.SimpleWordSet.add,org.eclipse.jdt.internal.core.index.DiskIndex.writeStreamChars,org.eclipse.jdt.internal.compiler.parser.Scanner.getNextToken0"
BENCHMARK_METHODS["fop"]="sun.nio.cs.StreamEncoder.writeBytes,org.apache.fop.fonts.FontInfo.getFontInstance,sun.nio.ch.UnixFileDispatcherImpl.read0,org.apache.fop.fo.properties.PropertyMaker.findProperty,org.apache.fop.fo.CharIterator.clone"
#BENCHMARK_METHODS["h2"]="org.h2.mvstore.db.ValueDataType.compare,org.h2.mvstore.Page.binarySearch,org.h2.mvstore.db.ValueDataType.compareValues,org.h2.mvstore.type.ObjectDataType\$LongType.compare,org.h2.value.DataType.getDataType"
#BENCHMARK_METHODS["jython"]="sun.nio.fs.UnixNativeDispatcher.open0,org.graalvm.collections.EconomicMapImpl.findLinear,org.graalvm.collections.EconomicMapImpl.getHashIndex,org.graalvm.collections.EconomicMapImpl.setValue,org.graalvm.collections.EconomicMapImpl.setRawValue"
#BENCHMARK_METHODS["pmd"]="org.dacapo.harness.TeeOutputStream.write,net.sf.saxon.om.Navigator\$DescendantEnumeration.advance,net.sf.saxon.pattern.NameTest.matches,org.jaxen.expr.DefaultNameStep.evaluate,org.jaxen.expr.DefaultLocationPath.evaluate"
BENCHMARK_METHODS["sunflow"]="org.sunflow.core.accel.KDTree.intersect,org.sunflow.core.primitive.TriangleMesh\$WaldTriangle.intersect,org.sunflow.core.accel.BoundingIntervalHierarchy.intersect,org.sunflow.core.accel.NullAccelerator.intersect,org.sunflow.core.Ray.transform"
#BENCHMARK_METHODS["tomcat"]="org.dacapo.harness.TeeOutputStream.write,org.dacapo.tomcat.Page.writeLog,sun.nio.cs.StreamEncoder.implClose,sun.nio.ch.SocketDispatcher.write0,sun.nio.cs.StreamEncoder.writeBytes"
#BENCHMARK_METHODS["xalan"]="sun.nio.cs.StreamEncoder.writeBytes,sun.nio.cs.StreamEncoder.implWrite,sun.nio.cs.StreamEncoder.write,org.apache.xml.serializer.ToStream.characters,org.apache.xml.dtm.ref.DTMManagerDefault.getDTM"


for benchmark in "${!BENCHMARK_METHODS[@]}"; do
    IFS=',' read -ra methods_array <<< "${BENCHMARK_METHODS[$benchmark]}"
    for method in "${methods_array[@]}"; do
        TARGET_METHOD="$method"
        for freq in "${AVAILABLE_FREQS[@]}"; do
            echo "Running benchmark '$benchmark' with TARGET_METHOD='$TARGET_METHOD' at frequency $freq"
            tmp_output=$(mktemp)
            mx --java-home=/openjdk-21/build/linux-x86_64-server-release/images/jdk \
               -J-Djava.library.path=/workspace/graal/vincent:$EFLECT_EXPERIMENTS/resources/bin \
               vm \
               -Dgraal.DVFSFrequency="$freq" -Dgraal.EnableDVFSCounterSampling=true -Dgraal.SampleRate=50000 \
               --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
               -javaagent:../joonhwan/agent-joon.jar="$TARGET_METHOD" \
               -cp "$DEPS_CP" \
               Harness \
               -c joonhwan.dacapo_callback.EnergyCallback \
               "$benchmark" -n $ITERATIONS > "$tmp_output" 2>&1
            measurement_line=$(grep "Measurement iteration:" "$tmp_output")
            if [ -n "$measurement_line" ]; then
                duration=$(echo "$measurement_line" | grep -oP '(?<=Duration = )\d+(?= ms)')
                energy=$(echo "$measurement_line" | grep -oP '(?<=Energy = )\d+(?= micro joules)')
                echo "Parsed results: Duration = ${duration} ms, Energy = ${energy} micro joules"
            else
                echo "No measurement iteration found for benchmark '$benchmark'."
            fi
            rm "$tmp_output"
        done
    done
done