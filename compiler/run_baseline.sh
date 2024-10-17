#! bin/bash
mx -J-Djava.library.path=/workspace/graal/vincent vm -Dgraal.EnableCustomIRProfiler=false \
    -Xmx10g \
    --add-opens jdk.graal.compiler/jdk.graal.compiler.hotspot.meta.joonhwan=ALL-UNNAMED \
    -jar ../dacapo/dacapo-9.12-bach.jar sunflow