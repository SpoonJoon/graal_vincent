#!/bin/bash
for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
   cpu_num=${cpu##*/cpu}
   current=$(cat $cpu/cpufreq/scaling_cur_freq 2>/dev/null)
   min=$(cat $cpu/cpufreq/scaling_min_freq 2>/dev/null)
   max=$(cat $cpu/cpufreq/scaling_max_freq 2>/dev/null)
   governor=$(cat $cpu/cpufreq/scaling_governor 2>/dev/null)
   if [ ! -z "$current" ]; then
       echo "CPU $cpu_num: Current: $(($current/1000)) MHz, Min: $(($min/1000)) MHz, Max: $(($max/1000)) MHz, Governor: $governor"
   fi
done
