#!/bin/bash

javac -cp .:./dacapo/dacapo-9.12-bach.jar \
       joonhwan/dacapo_callback/EnergyCallback.java \
       joonhwan/dacapo_callback/RaplPowercap.java

jar cvf energy-callback.jar \
       joonhwan/dacapo_callback/EnergyCallback.class \
       joonhwan/dacapo_callback/RaplPowercap.class
