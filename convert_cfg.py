#!/usr/bin/env python3
"""
CS443 Lab 2 — DOT to PNG Converter
Converts one or all .dot CFG files to PNG images using Graphviz.
"""

import os
import sys
import glob
import subprocess

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    cfg_dir = os.path.join(script_dir, "cfg_output")
    png_dir = os.path.join(script_dir, "cfg_png")

    if not os.path.isdir(cfg_dir):
        print(f"[!] No {os.path.basename(cfg_dir)}/ directory found. Run the analysis first.")
        sys.exit(1)

    os.makedirs(png_dir, exist_ok=True)

    # If a specific file is given as argument, convert only that
    if len(sys.argv) > 1:
        dot_file = sys.argv[1]
        if not os.path.isfile(dot_file):
            dot_file = os.path.join(cfg_dir, sys.argv[1])
        
        if not os.path.isfile(dot_file):
            print(f"[!] File not found: {sys.argv[1]}")
            sys.exit(1)

        basename = os.path.splitext(os.path.basename(dot_file))[0]
        png_out = os.path.join(png_dir, f"{basename}.png")
        
        print(f"[*] Converting: {dot_file}")
        try:
            subprocess.run(["dot", "-Tpng", dot_file, "-o", png_out], check=True)
            print(f"[✓] Output: {png_out}")
        except subprocess.CalledProcessError as e:
            print(f"[!] Error converting {dot_file}: {e}")
            sys.exit(1)
        except FileNotFoundError:
            print("[!] Graphviz 'dot' command not found. Please ensure Graphviz is installed.")
            sys.exit(1)
            
        sys.exit(0)

    # Convert all .dot files
    dot_files = glob.glob(os.path.join(cfg_dir, "*.dot"))
    if not dot_files:
        print(f"[!] No .dot files found in {cfg_dir}")
        sys.exit(0)

    count = 0
    for dot_file in dot_files:
        basename = os.path.splitext(os.path.basename(dot_file))[0]
        png_out = os.path.join(png_dir, f"{basename}.png")
        
        print(f"[*] Converting: {os.path.basename(dot_file)}")
        try:
            subprocess.run(["dot", "-Tpng", dot_file, "-o", png_out], check=True)
            count += 1
        except subprocess.CalledProcessError as e:
            print(f"[!] Error converting {dot_file}: {e}")
        except FileNotFoundError:
            print("[!] Graphviz 'dot' command not found. Please ensure Graphviz is installed.")
            sys.exit(1)

    print()
    print(f"[✓] Converted {count} CFGs to PNG")
    print(f"[✓] Output directory: {png_dir}/")

if __name__ == "__main__":
    main()
