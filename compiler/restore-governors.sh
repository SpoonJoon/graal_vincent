#!/bin/bash

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

cpu_cores=$(nproc)

restore_cpu() {
    local cpu=$1
    local base_path="/sys/devices/system/cpu/cpu${cpu}/cpufreq"
    
    # Set governor to powersave
    echo "ondemand" > ${base_path}/scaling_governor
    
    # No need to set scaling_setspeed as powersave governor ignores it
    
    # Verify change
    local governor=$(cat ${base_path}/scaling_governor)
    local freq=$(cat ${base_path}/scaling_cur_freq)
    echo "CPU${cpu}: Governor=${governor}, Frequency=${freq}kHz"
}

echo "Restoring all CPUs to powersave governor..."
for ((cpu=0; cpu<cpu_cores; cpu++)); do
    restore_cpu $cpu
done
