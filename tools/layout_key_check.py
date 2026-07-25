#!/usr/bin/env python3
"""
Layout Key Check Tool

Validates that layout parameter key computation is stable and deterministic.
Simulates layout parameter serialization and computes SHA-256 keys from
parameter combinations.

Tests:
1. Same input produces same key (stability)
2. Different inputs produce different keys (uniqueness)
3. Edge cases: zero values, very large values, RTL direction, etc.

Usage:
    python layout_key_check.py
"""

import hashlib
import json
import sys


def compute_layout_key(params: dict) -> str:
    """
    Compute a SHA-256 key from a set of layout parameters.
    
    The parameters are canonicalized (sorted by key) before hashing
    to ensure deterministic output regardless of dict insertion order.
    """
    canonical = json.dumps(params, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(canonical.encode('utf-8')).hexdigest()


def test_stability():
    """Test that the same input always produces the same key."""
    print("=" * 60)
    print("  Stability Tests")
    print("=" * 60)

    params = {
        "width": 1080,
        "height": 1920,
        "padding_left": 16,
        "padding_right": 16,
        "padding_top": 8,
        "padding_bottom": 8,
        "font_size": 18,
        "line_spacing": 1.6,
        "rtl": False,
        "text_align": "left"
    }

    key1 = compute_layout_key(params)
    key2 = compute_layout_key(params)
    key3 = compute_layout_key(dict(sorted(params.items())))

    stable = (key1 == key2 == key3)
    print(f"  Same input × 3:  {'PASS' if stable else 'FAIL'}")
    print(f"  Key:             {key1}")
    print(f"  Key (sorted):    {key3}")
    if not stable:
        print(f"  Found differing keys among identical inputs!")
        return False

    # Test with reordered keys (Python 3.7+ preserves insertion order by default,
    # but it's good to verify)
    reordered = {
        "text_align": "left",
        "rtl": False,
        "line_spacing": 1.6,
        "font_size": 18,
        "padding_bottom": 8,
        "padding_top": 8,
        "padding_right": 16,
        "padding_left": 16,
        "height": 1920,
        "width": 1080
    }
    key_reordered = compute_layout_key(reordered)
    stable_ordered = (key1 == key_reordered)
    print(f"  Reordered keys:  {'PASS' if stable_ordered else 'FAIL'}")
    if not stable_ordered:
        print(f"  Original: {key1}")
        print(f"  Reordered: {key_reordered}")
        return False

    return True


def test_uniqueness():
    """Test that different inputs produce different keys."""
    print()
    print("=" * 60)
    print("  Uniqueness Tests")
    print("=" * 60)

    test_cases = [
        ("Baseline", {"width": 1080, "height": 1920, "font_size": 18, "line_spacing": 1.6, "rtl": False}),
        ("Different font", {"width": 1080, "height": 1920, "font_size": 20, "line_spacing": 1.6, "rtl": False}),
        ("Different spacing", {"width": 1080, "height": 1920, "font_size": 18, "line_spacing": 2.0, "rtl": False}),
        ("RTL enabled", {"width": 1080, "height": 1920, "font_size": 18, "line_spacing": 1.6, "rtl": True}),
        ("Different resolution", {"width": 1440, "height": 2560, "font_size": 18, "line_spacing": 1.6, "rtl": False}),
    ]

    keys = {}
    all_unique = True

    for name, params in test_cases:
        key = compute_layout_key(params)
        keys[name] = key
        print(f"  {name:25s}: {key}")

    # Check that all keys are unique
    unique_keys = set(keys.values())
    if len(unique_keys) != len(test_cases):
        print()
        print(f"  FAIL: Only {len(unique_keys)} unique key(s) out of {len(test_cases)} test cases")
        # Find duplicates
        seen = {}
        for name, key in keys.items():
            if key in seen:
                print(f"    Duplicate: '{seen[key]}' and '{name}'")
            seen[key] = name
        all_unique = False

    return all_unique


def test_edge_cases():
    """Test edge cases that could cause key instability."""
    print()
    print("=" * 60)
    print("  Edge Case Tests")
    print("=" * 60)

    edge_cases = [
        ("Zero values", {"width": 0, "height": 0, "padding": 0}),
        ("Negative values", {"width": -1, "height": -1, "offset": -100}),
        ("Very large values", {"width": 999999, "height": 999999, "count": 2**31 - 1}),
        ("Float precision", {"scale": 1.0, "ratio": 0.3333333333333333}),
        ("Float precision 2", {"scale": 1.0, "ratio": 0.3333333333333334}),
        ("Empty params", {}),
        ("Single param", {"enabled": True}),
        ("Boolean toggles", {"rtl": True, "landscape": False, "invert": True}),
        ("String values", {"direction": "ltr", "alignment": "justify"}),
        ("Mixed types", {"count": 42, "ratio": 3.14, "name": "test", "active": True}),
    ]

    all_stable = True
    all_unique = True
    keys = {}

    for name, params in edge_cases:
        key = compute_layout_key(params)
        keys[name] = key
        # Verify stability (compute twice)
        key2 = compute_layout_key(params)
        stable = (key == key2)
        if not stable:
            print(f"  {name:25s}: UNSTABLE (key1={key}, key2={key2})")
            all_stable = False
        else:
            print(f"  {name:25s}: {key}")

    # Check uniqueness among edge cases
    unique_keys = set(keys.values())
    if len(unique_keys) != len(edge_cases):
        print()
        print(f"  Note: {len(edge_cases) - len(unique_keys)} edge case(s) produced duplicate keys")
        seen = {}
        for name, key in keys.items():
            if key in seen:
                print(f"    Duplicate: '{seen[key]}' and '{name}'")
            seen[key] = name

    return all_stable


def main():
    print("Layout Key Check Tool")
    print("=" * 60)
    print()

    stability_ok = test_stability()
    uniqueness_ok = test_uniqueness()
    edge_cases_ok = test_edge_cases()

    print()
    print("=" * 60)
    print("  Summary")
    print("=" * 60)
    print(f"  Stability test:  {'PASS' if stability_ok else 'FAIL'}")
    print(f"  Uniqueness test: {'PASS' if uniqueness_ok else 'FAIL'}")
    print(f"  Edge cases:      {'PASS' if edge_cases_ok else 'FAIL'}")

    all_pass = stability_ok and uniqueness_ok and edge_cases_ok
    print(f"  Overall:         {'PASS' if all_pass else 'SOME FAILURES'}")
    print()

    return 0 if all_pass else 1


if __name__ == "__main__":
    sys.exit(main())
