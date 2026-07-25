#!/usr/bin/env python3
"""
Encoding Detection Probe

Auxiliary tool to detect the encoding of a text file using the chardet library.
Reads the first 64KB of a file and reports encoding detection results.

Usage:
    python encoding_probe.py <filepath>

Dependencies:
    pip install chardet

Output:
    - File path
    - Detected encoding
    - Confidence score
    - BOM information
"""

import sys
import os


def detect_bom(data: bytes):
    """Detect Byte Order Mark (BOM) in the data."""
    if len(data) >= 3 and data[:3] == b'\xef\xbb\xbf':
        return "UTF-8 BOM (EF BB BF)"
    elif len(data) >= 2 and data[:2] == b'\xfe\xff':
        return "UTF-16 BE BOM (FE FF)"
    elif len(data) >= 2 and data[:2] == b'\xff\xfe':
        return "UTF-16 LE BOM (FF FE)"
    elif len(data) >= 4 and data[:4] == b'\x00\x00\xfe\xff':
        return "UTF-32 BE BOM (00 00 FE FF)"
    elif len(data) >= 4 and data[:4] == b'\xff\xfe\x00\x00':
        return "UTF-32 LE BOM (FF FE 00 00)"
    return None


def read_head_bytes(filepath: str, max_size: int = 65536) -> bytes:
    """Read the first max_size bytes of a file."""
    with open(filepath, 'rb') as f:
        return f.read(max_size)


def main():
    if len(sys.argv) < 2:
        print("Usage: python encoding_probe.py <filepath>", file=sys.stderr)
        sys.exit(1)

    filepath = sys.argv[1]

    if not os.path.exists(filepath):
        print(f"Error: File not found: {filepath}", file=sys.stderr)
        sys.exit(1)

    if not os.path.isfile(filepath):
        print(f"Error: Not a file: {filepath}", file=sys.stderr)
        sys.exit(1)

    file_size = os.path.getsize(filepath)
    print(f"File: {filepath}")
    print(f"Size: {file_size} bytes")
    print()

    try:
        data = read_head_bytes(filepath)
        actual_bytes_read = min(file_size, 65536)
        print(f"Bytes analyzed: {actual_bytes_read}")
        print()

        # BOM detection
        bom_info = detect_bom(data)
        if bom_info:
            print(f"BOM: {bom_info}")
        else:
            print("BOM: None detected")
        print()

        # chardet detection
        try:
            import chardet
            result = chardet.detect(data)
            encoding = result.get('encoding', 'unknown')
            confidence = result.get('confidence', 0.0)
            language = result.get('language', '')

            print(f"Detected encoding: {encoding}")
            print(f"Confidence:        {confidence:.4f} ({confidence * 100:.2f}%)")
            if language:
                print(f"Language:          {language}")
            print()

            # Additional info for common encodings
            if encoding and encoding.upper() in ('GB2312', 'GBK', 'GB18030'):
                print("Note: This is a Chinese character encoding.")
                print("      GB18030 is the successor to GBK and GB2312.")
            elif encoding and 'SHIFT_JIS' in encoding.upper():
                print("Note: This is a Japanese character encoding (Shift JIS).")
            elif encoding and 'EUC' in encoding.upper():
                print("Note: This is an East Asian encoding (EUC).")
            elif encoding and encoding.upper() == 'ISO-8859-1':
                print("Note: ISO-8859-1 (Latin-1) is a single-byte Western encoding.")
                print("      Consider UTF-8 for compatibility.")

        except ImportError:
            print("Warning: 'chardet' library not installed.", file=sys.stderr)
            print("Install with: pip install chardet", file=sys.stderr)
            print()
            print("Fallback: Basic encoding check only.")
            print()

            # Basic encoding check
            try:
                data[:1024].decode('utf-8')
                print("Valid UTF-8: Yes")
            except UnicodeDecodeError:
                print("Valid UTF-8: No")

    except Exception as e:
        print(f"Error analyzing file: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
