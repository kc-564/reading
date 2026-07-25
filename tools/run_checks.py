#!/usr/bin/env python3
"""
Run All Checks

One-click script to run all validation tools in sequence.
Exits with non-zero code if any script fails.

Usage:
    python run_checks.py [--all]
    
Options:
    --all    Also run encoding_probe.py (requires a file argument in interactive use)
            and layout_key_check.py
"""

import subprocess
import sys
import os


def run_script(script_name: str, args: list = None) -> bool:
    """Run a Python script and return True if it succeeds."""
    script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), script_name)
    
    if not os.path.exists(script_path):
        print(f"[ERROR] Script not found: {script_path}")
        return False

    cmd = [sys.executable, script_path]
    if args:
        cmd.extend(args)

    print(f"\n{'=' * 60}")
    print(f"  Running: {script_name}")
    print(f"{'=' * 60}")
    print()

    result = subprocess.run(cmd, capture_output=False)

    if result.returncode != 0:
        print(f"\n[FAIL] {script_name} exited with code {result.returncode}")
        return False
    else:
        print(f"\n[PASS] {script_name} completed successfully")
        return True


def main():
    print("=" * 60)
    print("  Run All Checks")
    print("=" * 60)

    all_args = "--all" in sys.argv or "-a" in sys.argv

    results = []

    # Always run toc_validate
    results.append(("toc_validate.py", run_script("toc_validate.py")))

    # Run layout_key_check if --all is specified
    if all_args:
        results.append(("layout_key_check.py", run_script("layout_key_check.py")))

    # Summary
    print()
    print("=" * 60)
    print("  Overall Results")
    print("=" * 60)

    all_pass = True
    for name, passed in results:
        status = "PASS" if passed else "FAIL"
        print(f"  {name:30s} {status}")
        if not passed:
            all_pass = False

    print()
    if all_pass:
        print("  All checks passed!")
    else:
        print("  Some checks failed. See output above for details.")

    print()
    return 0 if all_pass else 1


if __name__ == "__main__":
    sys.exit(main())
