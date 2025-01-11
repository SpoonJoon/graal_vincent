#!/bin/bash
for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
   cpu_num=${cpu##*/cpu}
   current=$(cat $cpu/cpufreq/scaling_cur_freq 2>/dev/null)
   [ ! -z "$current" ] && echo "CPU $cpu_num: $(($current/1000)) MHz"
done
