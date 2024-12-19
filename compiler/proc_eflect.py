#!/usr/bin/env python3

import os
import sys
import pandas as pd
import numpy as np

def load_footprint(path):
    return pd.read_csv(path, parse_dates=['start', 'end'])

def filter_to_app(trace):
    trace = trace.split(';')
    while len(trace) > 0:
        if r'.' not in trace[0] or '_' in trace[0] or '::' in trace[0] or r'java' in trace[0] \
           or 'jdk' in trace[0] or 'eflect' in trace[0] or 'chappie' in trace[0] or r'.so' in trace[0]:
            trace.pop()
        else:
            return trace[0]
    return None

def analyze_method_energy(footprint_path, top_n=10):
    df = load_footprint(footprint_path)
    df = df.dropna(subset=['trace'])
    
    df['duration'] = (pd.to_datetime(df['end']) - pd.to_datetime(df['start'])).dt.total_seconds()
    df['power'] = df['app_energy'] / df['duration']
    df['method'] = df['trace'].apply(filter_to_app)
    method_energy = df.groupby('method')['app_energy'].sum().sort_values(ascending=False)
    
    top_methods = method_energy.head(top_n)
    total_energy = method_energy.sum()
    top_methods_df = pd.DataFrame({
        'Energy (J)': top_methods,
        'Percentage': (top_methods / total_energy * 100).round(2)
    })
    
    return top_methods_df

def main():
    if len(sys.argv) != 3:
        print("Usage: python script.py <footprint_file> <top_n>", file=sys.stderr)
        sys.exit(1)
        
    footprint_file = sys.argv[1]
    try:
        top_n = int(sys.argv[2])
    except ValueError:
        print("Error: top_n must be an integer", file=sys.stderr)
        sys.exit(1)
        
    try:
        results = analyze_method_energy(footprint_file, top_n)
        methods = results.index.tolist()
        print(','.join(methods))
    except Exception as e:
        print(f"Error processing file: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()