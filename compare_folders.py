#!/usr/bin/env python3
import os
import sys

SOURCE1 = sys.argv[1] if len(sys.argv) > 1 else \
    "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/transformer-agent/spoon-javaparser-commits.txt"
SOURCE2 = sys.argv[2] if len(sys.argv) > 2 else \
    "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/transformation-rules-report/agent/gemini/javaparser"

def read_entries(source):
    if os.path.isfile(source):
        with open(source) as f:
            return [line.strip() for line in f if line.strip()]
    elif os.path.isdir(source):
        return sorted(os.listdir(source))
    else:
        print(f"ERROR: '{source}' is not a file or directory.")
        sys.exit(1)

list1 = read_entries(SOURCE1)
list2 = read_entries(SOURCE2)

set1 = set(list1)
set2 = set(list2)

matches = sorted(set1 & set2)
only_in_1 = sorted(set1 - set2)
only_in_2 = sorted(set2 - set1)

print(f"=== Source 1: {SOURCE1} ({len(list1)} entries) ===")
print(f"=== Source 2: {SOURCE2} ({len(list2)} entries) ===")
print()

print(f"=== MATCHES ({len(matches)}) ===")
for m in matches:
    print(f"  {m}")

print()
print(f"=== In SOURCE1 but NOT in SOURCE2 ({len(only_in_1)}) ===")
for m in only_in_1:
    print(f"  {m}")

print()
print(f"=== In SOURCE2 but NOT in SOURCE1 ({len(only_in_2)}) ===")
for m in only_in_2:
    print(f"  {m}")
