#!/usr/bin/env python3
"""
TOC Rule Validator

Reads TOC rules from app/src/main/assets/toc_rules.json and tests them against
sample title files and body text files to compute recall and precision metrics.

Usage:
    python tools/toc_validate.py

The script:
1. Loads rules from toc_rules.json
2. Tests each rule against sample title files in samples/titles/
3. Tests for false positives against sample body files in samples/body/
4. Tests lookbehind rules against samples in samples/lookbehind/
5. Outputs recall and precision statistics
6. If baseline.json exists, compares results to baseline
"""

import json
import os
import re
import sys

# Paths
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "toc_rules.json")
SAMPLES_DIR = os.path.join(PROJECT_ROOT, "tools", "samples")
TITLES_DIR = os.path.join(SAMPLES_DIR, "titles")
BODY_DIR = os.path.join(SAMPLES_DIR, "body")
LOOKBEHIND_DIR = os.path.join(SAMPLES_DIR, "lookbehind")
BASELINE_PATH = os.path.join(PROJECT_ROOT, "tools", "baseline.json")


def load_rules():
    """Load TOC rules from JSON file."""
    if not os.path.exists(RULES_PATH):
        print(f"[WARNING] Rules file not found: {RULES_PATH}")
        return []

    try:
        with open(RULES_PATH, "r", encoding="utf-8") as f:
            rules = json.load(f)
        print(f"[INFO] Loaded {len(rules)} rule(s) from {RULES_PATH}")
        return rules
    except json.JSONDecodeError as e:
        print(f"[ERROR] Invalid JSON in rules file: {e}")
        return []
    except Exception as e:
        print(f"[ERROR] Failed to load rules: {e}")
        return []


def load_sample_lines(directory):
    """
    Load sample lines from all files in a directory.
    
    Format: Each line is a test sample.
    Lines starting with '#' are comments and are skipped.
    Empty lines are skipped.
    """
    if not os.path.exists(directory):
        print(f"[INFO] Sample directory not found: {directory}")
        return []

    lines = []
    for filename in sorted(os.listdir(directory)):
        filepath = os.path.join(directory, filename)
        if not os.path.isfile(filepath) or filename.startswith('.'):
            continue
        
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                for line in f:
                    stripped = line.strip()
                    if stripped and not stripped.startswith('#'):
                        lines.append(stripped)
            print(f"[INFO] Loaded samples from {filename}")
        except Exception as e:
            print(f"[WARNING] Failed to read {filepath}: {e}")

    return lines


def test_rules_on_lines(rules, lines):
    """
    Test all rules against a list of lines.
    
    Returns:
        - hit_lines: set of line indices that matched at least one rule
        - rule_hits: dict mapping rule index to list of matched line indices
    """
    hit_lines = set()
    rule_hits = {i: [] for i in range(len(rules))}

    for line_idx, line in enumerate(lines):
        for rule_idx, rule in enumerate(rules):
            pattern = rule.get("pattern", "")
            negative_filters = rule.get("negativeFilters", [])

            try:
                regex = re.compile(pattern, re.MULTILINE)
                if regex.match(line):
                    # Check negative filters
                    should_skip = False
                    for neg_pattern in negative_filters:
                        try:
                            neg_regex = re.compile(neg_pattern, re.MULTILINE)
                            if neg_regex.search(line):
                                should_skip = True
                                break
                        except re.error:
                            continue
                    
                    if not should_skip:
                        hit_lines.add(line_idx)
                        rule_hits[rule_idx].append(line_idx)
            except re.error as e:
                print(f"[WARNING] Invalid regex in rule {rule_idx}: {pattern} - {e}")
                continue

    return hit_lines, rule_hits


def test_lookbehind_rules(rules, directory):
    """
    Test lookbehind rules against special samples.
    
    Lookbehind samples contain multi-line test cases where each block
    is separated by an empty line or a comment line.
    """
    if not os.path.exists(directory):
        return 0, 0, []

    total_tests = 0
    passed_tests = 0
    failures = []

    for filename in sorted(os.listdir(directory)):
        filepath = os.path.join(directory, filename)
        if not os.path.isfile(filepath) or filename.startswith('.'):
            continue

        try:
            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()
            
            # Split into test blocks by double newline
            blocks = re.split(r'\n\s*\n', content)
            
            for block_idx, block in enumerate(blocks):
                block = block.strip()
                if not block or block.startswith('#'):
                    continue
                
                total_tests += 1
                lines = block.split('\n')
                
                # Check if any rule matches
                matched = False
                for rule in rules:
                    pattern = rule.get("pattern", "")
                    try:
                        regex = re.compile(pattern, re.MULTILINE)
                        for line in lines:
                            if regex.match(line.strip()):
                                matched = True
                                break
                    except re.error:
                        continue
                    if matched:
                        break
                
                if matched:
                    passed_tests += 1
                else:
                    failures.append(f"  [{filename}:block{block_idx}] No rule matched:\n    {lines[0][:80]}")
                    
        except Exception as e:
            print(f"[WARNING] Failed to process lookbehind file {filepath}: {e}")

    return total_tests, passed_tests, failures


def calculate_metrics(title_hits, title_total, body_hits, body_total):
    """Calculate recall and precision metrics."""
    recall = (title_hits / title_total * 100) if title_total > 0 else 0.0
    false_positive_rate = (body_hits / body_total * 100) if body_total > 0 else 0.0
    precision = (1 - body_hits / max(title_hits + body_hits, 1)) * 100
    return recall, precision, false_positive_rate


def load_baseline():
    """Load baseline metrics if available."""
    if not os.path.exists(BASELINE_PATH):
        return None
    
    try:
        with open(BASELINE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        print(f"[WARNING] Failed to load baseline: {e}")
        return None


def check_baseline_deviation(current, baseline):
    """Check if current metrics deviate from baseline beyond tolerance (±5%)."""
    tolerance = 5.0
    warnings = []
    
    if baseline is None:
        return warnings
    
    baseline_metrics = baseline.get("toc_validate", {})
    
    for key in ["title_hit_rate", "body_false_positive_rate"]:
        current_val = current.get(key, 0)
        baseline_val = baseline_metrics.get(key, 0)
        
        if baseline_val == 0 and current_val > tolerance:
            warnings.append(f"[WARNING] {key}: {current_val:.1f}% vs baseline {baseline_val:.1f}% (deviation > {tolerance}%)")
        elif baseline_val > 0:
            deviation = abs(current_val - baseline_val)
            if deviation > tolerance:
                warnings.append(f"[WARNING] {key}: {current_val:.1f}% vs baseline {baseline_val:.1f}% (deviation {deviation:.1f}% > {tolerance}%)")
    
    return warnings


def main():
    print("=" * 60)
    print("  TOC Rule Validation")
    print("=" * 60)
    print()

    # Load rules
    rules = load_rules()
    if not rules:
        print("[INFO] No rules to validate. Exiting.")
        return 0

    # Load title samples
    title_lines = load_sample_lines(TITLES_DIR)
    print(f"[INFO] Loaded {len(title_lines)} title sample line(s)")
    print()

    # Test rules on titles
    title_hits, title_rule_hits = test_rules_on_lines(rules, title_lines)
    title_total = len(title_lines)
    title_hit_count = len(title_hits)

    print(f"Title Detection Results:")
    print(f"  Total title samples: {title_total}")
    print(f"  Matched by rules:    {title_hit_count}")
    print(f"  Missed:              {title_total - title_hit_count}")
    
    # Per-rule breakdown
    for rule_idx, hits in title_rule_hits.items():
        rule = rules[rule_idx]
        pattern = rule.get("pattern", "")
        priority = rule.get("priority", 0)
        print(f"  Rule {rule_idx} (priority={priority}): {len(hits)} hits — pattern: {pattern[:60]}")
    
    print()

    # Load body samples
    body_lines = load_sample_lines(BODY_DIR)
    print(f"[INFO] Loaded {len(body_lines)} body sample line(s)")
    print()

    # Test rules on body (false positive check)
    body_hits, body_rule_hits = test_rules_on_lines(rules, body_lines)
    body_total = len(body_lines)
    body_hit_count = len(body_hits)

    print(f"Body False Positive Results:")
    print(f"  Total body samples:   {body_total}")
    print(f"  False positives:      {body_hit_count}")
    print(f"  Clean lines:          {body_total - body_hit_count}")
    print()

    # Test lookbehind rules
    lookbehind_total, lookbehind_passed, lookbehind_failures = test_lookbehind_rules(rules, LOOKBEHIND_DIR)
    if lookbehind_total > 0:
        print(f"Lookbehind Test Results:")
        print(f"  Total tests:  {lookbehind_total}")
        print(f"  Passed:       {lookbehind_passed}")
        print(f"  Failed:       {lookbehind_total - lookbehind_passed}")
        for failure in lookbehind_failures:
            print(failure)
        print()

    # Calculate metrics
    recall, precision, fpr = calculate_metrics(title_hit_count, title_total, body_hit_count, body_total)

    print("=" * 60)
    print("  Summary Metrics")
    print("=" * 60)
    print(f"  Recall (title hit rate):          {recall:.2f}%")
    print(f"  Precision:                        {precision:.2f}%")
    print(f"  False Positive Rate (body):        {fpr:.2f}%")
    print()

    # Baseline comparison
    baseline = load_baseline()
    if baseline is not None:
        current_metrics = {
            "title_hit_rate": recall,
            "body_false_positive_rate": fpr,
            "sample_count": title_total + body_total
        }
        warnings = check_baseline_deviation(current_metrics, baseline)
        if warnings:
            print("Baseline Comparison:")
            for w in warnings:
                print(f"  {w}")
            print()
        else:
            print("Baseline Comparison: All metrics within tolerance.")
            print()

    # Determine exit code
    if title_total > 0 and title_hit_count == 0:
        print("[WARNING] No title samples were matched by any rule.")
        print("[INFO] This may be expected if no samples exist yet.")
    
    if body_hit_count > 0:
        print(f"[INFO] {body_hit_count} body line(s) triggered false positives.")
        if fpr > 20:
            print("[WARNING] False positive rate exceeds 20%. Consider refining rules.")

    print()
    print("Validation complete.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
