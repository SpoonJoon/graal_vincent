#!/bin/bash
EFLECT_HOME=/workspace/eflect
EFLECT_EXPERIMENTS=$EFLECT_HOME/experiments
DEPS_DIR=$EFLECT_EXPERIMENTS/resources/jar

DEPS_CP="$DEPS_DIR/dacapo.jar"

javac -cp .:$DEPS_CP \
       joonhwan/dacapo_callback/EnergyCallback.java \
       joonhwan/dacapo_callback/RaplPowercap.java

jar cvf joonhwan/energy-callback.jar \
       joonhwan/dacapo_callback/EnergyCallback.class \
       joonhwan/dacapo_callback/RaplPowercap.class
