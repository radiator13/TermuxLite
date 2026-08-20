#!/usr/bin/env python3
"""
Real-time SLOC (Source Lines of Code) Tracker for TermuxLite.
Tracks all source files, languages, comments, blanks, and code lines.
Can automatically update README.md and generate release reports.
"""

import os
import sys
import json
import re

EXTENSIONS = {
    '.kt': 'Kotlin',
    '.java': 'Java',
    '.cpp': 'C/C++',
    '.hpp': 'C/C++',
    '.c': 'C/C++',
    '.h': 'C/C++',
    '.gradle': 'Gradle (Groovy/Kotlin)',
    '.cmake': 'CMake',
    '.xml': 'XML',
    '.py': 'Python',
    '.sh': 'Shell',
    '.properties': 'Properties',
    '.pro': 'Proguard',
}

IGNORE_DIRS = {'.git', '.gradle', 'build', 'dist', '.idea', 'gradle/wrapper'}
IGNORE_FILES = {'gradlew', 'gradlew.bat', 'gradle-wrapper.jar'}

def analyze_file(filepath, ext):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()
    except Exception:
        return 0, 0, 0, 0

    total_lines = len(lines)
    blank_lines = 0
    comment_lines = 0
    code_lines = 0
    in_multiline = False

    for line in lines:
        s = line.strip()
        if not s:
            blank_lines += 1
            continue

        if ext in ('.kt', '.java', '.cpp', '.hpp', '.c', '.h', '.gradle', '.pro'):
            if in_multiline:
                comment_lines += 1
                if '*/' in s:
                    in_multiline = False
                continue
            if s.startswith('/*'):
                comment_lines += 1
                if '*/' not in s or s.count('/*') > s.count('*/'):
                    in_multiline = True
                continue
            if s.startswith('//'):
                comment_lines += 1
                continue
            code_lines += 1

        elif ext == '.xml':
            if in_multiline:
                comment_lines += 1
                if '-->' in s:
                    in_multiline = False
                continue
            if s.startswith('<!--'):
                comment_lines += 1
                if '-->' not in s:
                    in_multiline = True
                continue
            code_lines += 1

        elif ext in ('.py', '.sh'):
            if in_multiline:
                comment_lines += 1
                if '"""' in s or "'''" in s:
                    in_multiline = False
                continue
            if s.startswith('"""') or s.startswith("'''"):
                comment_lines += 1
                if s.count('"""') == 1 or s.count("'''") == 1:
                    in_multiline = True
                continue
            if s.startswith('#'):
                comment_lines += 1
                continue
            code_lines += 1

        elif ext == '.properties':
            if s.startswith('#') or s.startswith('!'):
                comment_lines += 1
                continue
            code_lines += 1
        else:
            code_lines += 1

    return total_lines, code_lines, comment_lines, blank_lines

def scan_repo(repo_root='.'):
    results = {}
    file_count = 0

    for root, dirs, files in os.walk(repo_root):
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
        for f in files:
            if f in IGNORE_FILES:
                continue
            _, ext = os.path.splitext(f)
            if f == 'CMakeLists.txt':
                ext = '.cmake'
            if ext in EXTENSIONS:
                lang = EXTENSIONS[ext]
                filepath = os.path.join(root, f)
                tot, code, comment, blank = analyze_file(filepath, ext)
                if lang not in results:
                    results[lang] = {
                        'files': 0,
                        'code': 0,
                        'comments': 0,
                        'blanks': 0,
                        'total': 0
                    }
                results[lang]['files'] += 1
                results[lang]['code'] += code
                results[lang]['comments'] += comment
                results[lang]['blanks'] += blank
                results[lang]['total'] += tot
                file_count += 1

    return results

def generate_markdown_table(stats):
    total_code = sum(s['code'] for s in stats.values())
    total_files = sum(s['files'] for s in stats.values())
    total_comments = sum(s['comments'] for s in stats.values())
    total_blanks = sum(s['blanks'] for s in stats.values())
    total_lines = sum(s['total'] for s in stats.values())

    lines = [
        "| Language | Files | Code (SLOC) | Comments | Blanks | Total Lines | % of Code |",
        "| :--- | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]

    for lang, s in sorted(stats.items(), key=lambda x: x[1]['code'], reverse=True):
        pct = (s['code'] / total_code * 100) if total_code > 0 else 0
        lines.append(f"| **{lang}** | {s['files']} | {s['code']:,} | {s['comments']:,} | {s['blanks']:,} | {s['total']:,} | {pct:.1f}% |")

    lines.append(f"| **Total** | **{total_files}** | **{total_code:,}** | **{total_comments:,}** | **{total_blanks:,}** | **{total_lines:,}** | **100%** |")
    return "\n".join(lines), total_code

def update_readme(readme_path, stats):
    if not os.path.exists(readme_path):
        return False

    with open(readme_path, 'r', encoding='utf-8') as f:
        content = f.read()

    table, total_code = generate_markdown_table(stats)
    k_sloc = f"{total_code / 1000:.1f}k" if total_code >= 1000 else str(total_code)

    # Replace badge or SLOC text in README
    # Pattern: <!-- SLOC_START --> ... <!-- SLOC_END -->
    sloc_block = f"<!-- SLOC_START -->\n{table}\n<!-- SLOC_END -->"

    if "<!-- SLOC_START -->" in content and "<!-- SLOC_END -->" in content:
        new_content = re.sub(
            r'<!-- SLOC_START -->.*?<!-- SLOC_END -->',
            sloc_block,
            content,
            flags=re.DOTALL
        )
    else:
        # Update existing SLOC numbers dynamically
        new_content = re.sub(r'under [\d\.]+k lines of code \(SLOC\)', f'under {total_code:,} lines of code (SLOC)', content)
        new_content = re.sub(r'~[\d\.]+k SLOC', f'{total_code:,} SLOC', new_content)
        new_content = re.sub(r'in ~[\d\.]+k SLOC', f'in ~{k_sloc} SLOC', new_content)

    if new_content != content:
        with open(readme_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    stats = scan_repo(repo_root)
    table, total_code = generate_markdown_table(stats)

    print(f"📊 Real-time SLOC Breakdown ({total_code:,} total SLOC):")
    print(table)

    if '--json' in sys.argv:
        idx = sys.argv.index('--json')
        out = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else 'sloc.json'
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        with open(out, 'w', encoding='utf-8') as f:
            json.dump({'total_sloc': total_code, 'languages': stats}, f, indent=2)
        print(f"Saved JSON report to {out}")

    if '--markdown' in sys.argv:
        idx = sys.argv.index('--markdown')
        out = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else 'SLOC.md'
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        with open(out, 'w', encoding='utf-8') as f:
            f.write(f"# 📊 TermuxLite Source Lines of Code (SLOC)\n\n**Total SLOC:** {total_code:,}\n\n{table}\n")
        print(f"Saved Markdown report to {out}")

    if '--update-readme' in sys.argv:
        readme = os.path.join(repo_root, 'README.md')
        updated = update_readme(readme, stats)
        if updated:
            print(f"✅ Updated {readme} with latest SLOC count ({total_code:,}).")
        else:
            print(f"ℹ️ {readme} already has up-to-date SLOC count.")

if __name__ == '__main__':
    main()
