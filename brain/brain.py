#!/usr/bin/env python3
"""AI BRAIN 2.0 - a memory + token-saver for any codebase. Pure Python 3.8+ standard library. No network. Never runs your code.

START   brief ["task words"]   the ONE command to begin a session: terse rules + project + status + handoff (+ task context). ~600 tokens.
FIND    ctx "task"             task-aware retrieval: the files, symbols, tests, lessons and router rows for what you are about to do
        find NAME              where is it defined?              outline FILE   every symbol of a file, with line numbers
        show NAME [--lines N]  the body of a symbol (capped)     map [DIR]      STRUCTURE.md for one folder only
        guide [SECTION]        one AI_GUIDE section, or the list of sections with their token cost
IMPACT  impact NAME            who calls / imports it, which tests, blast radius      tests-for FILE|NAME   tests that cover it
RUN     run "cmd" [--max N]    run a command, print only failures + summary (ANSI stripped, repeats folded); full log kept
        diff [--hunks]         compact diff: files + purpose + size                   cost   what reading things would cost in tokens
KEEP    learn "text" [--ref a,b]   add a lesson (docs/LESSONS.md)        audit   stale / broken / contradicting lessons, router, guide, plans
        handoff ["note"]       write docs/HANDOFF.md so the next chat resumes exactly here
        secrets [--staged|--all]   find keys/tokens before they are committed
SYSTEM  init [--tool all|claude,cursor,copilot,windsurf,gemini,agents,cline,aider] [--no-hooks]   index   structure [--draft]   inventory
        start   check [--changed]   hooks   selftest   version
Config: brain/brain.json. Languages: Python, JS/TS, Go, Rust, Java/Kotlin/C#, Ruby, PHP, C/C++, Swift, Dart, shell, SQL, HTML ids, CSS classes.
"""
import datetime, fnmatch, json, math, os, re, subprocess, sys

VERSION = '2.0.0'
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)          # brain/ lives at the project root
CACHE = os.path.join(HERE, '.cache')
CFG_NAME = 'brain.json'
TERSE = ('OUTPUT RULES (terse): no narration ("let me check..."), no recap of what you just did, no asking permission for obvious steps, '
         'code/diff first. Final reply = what changed / what could not be verified / what the owner must decide.')

LIST_KEYS = ('ignore_dirs', 'ignore_globs', 'structure_ignore', 'index_ignore_globs')
DEFAULT_CFG = {
    "project": "",
    "ignore_dirs": [".git", "node_modules", "dist", "build", "out", "target", "vendor", "__pycache__", ".venv", "venv", ".next", ".nuxt", ".cache",
                    "coverage", ".idea", ".vscode", ".gradle", "Pods", "bin", "obj", ".terraform", ".pytest_cache"],
    "ignore_globs": ["*.pyc", "*.min.js", "*.min.css", "*.map", "*.lock", "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "*.png", "*.jpg", "*.jpeg",
                     "*.gif", "*.webp", "*.ico", "*.svg", "*.woff", "*.woff2", "*.ttf", "*.pdf", "*.zip", "*.mp3", "*.mp4", "*.mov", "*.sqlite", "*.db", ".DS_Store"],
    "structure_ignore": ["symbols.idx", "brain/*", ".claude/*", ".githooks/*", ".cursor/*", ".windsurf/*", ".github/copilot-instructions.md", "AI_GUIDE.md",
                         "STRUCTURE.md", "CLAUDE.md", "AGENTS.md", "GEMINI.md", "CONVENTIONS.md", ".cursorrules", ".windsurfrules", ".clinerules",
                         "docs/ALL_FEATURES.md", "docs/LESSONS.md", "docs/HANDOFF.md"],
    "index_ignore_globs": ["brain/*", ".claude/*"],
    "max_index_file_kb": 1500,
    "commands": {"build": "", "syntax": "", "tests": []},
    "test_cmd": "",                       # template with {test}, used by `check --changed`; auto-detected on init
    "plans_glob": "docs/*PLAN*.md",
    "main_branch": "main",
}

# ------------------------------------------------------------------ helpers
def sh(cmd, cwd=None, timeout=900):
    try:
        r = subprocess.run(cmd, shell=True, cwd=cwd or ROOT, capture_output=True, text=True, timeout=timeout, errors='replace')
        return r.returncode, (r.stdout or '').strip(), (r.stderr or '').strip()
    except subprocess.TimeoutExpired:
        return 124, '', 'timeout after %ss' % timeout

def cfg():
    p = os.path.join(HERE, CFG_NAME)
    c = json.loads(json.dumps(DEFAULT_CFG))
    if os.path.exists(p):
        try:
            u = json.load(open(p, encoding='utf-8'))
        except Exception as e:
            print('brain.json is not valid JSON:', e); sys.exit(2)
        for k, v in u.items():
            c[k] = sorted(set(c[k]) | set(v)) if k in LIST_KEYS and isinstance(v, list) else v
    return c

def read(rel):
    try:
        return open(os.path.join(ROOT, rel), encoding='utf-8', errors='replace').read()
    except Exception:
        return ''

def write(rel, text):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p) or '.', exist_ok=True)
    open(p, 'w', encoding='utf-8').write(text)

def est(s):
    """Rough token estimate (chars / 3.8). Good enough to budget; real tokenizers differ by +-15%."""
    return int(math.ceil(len(s) / 3.8))

def is_binary(path):
    try:
        with open(path, 'rb') as f:
            return b'\0' in f.read(2048)
    except Exception:
        return True

def today():
    return datetime.date.today().isoformat()

_FILES = None
def project_files(c=None):
    """Relative paths of every project file: git-tracked + untracked-not-ignored (or a directory walk when there is no git)."""
    global _FILES
    if _FILES is not None:
        return _FILES
    c = c or cfg()
    rc, out, _ = sh('git ls-files -co --exclude-standard')
    files = [f for f in out.split('\n') if f] if rc == 0 and out else []
    if not files:
        for d, ds, fs in os.walk(ROOT):
            ds[:] = [x for x in sorted(ds) if x not in c['ignore_dirs']]
            files += [os.path.relpath(os.path.join(d, f), ROOT) for f in sorted(fs)]
    keep = []
    for f in files:
        f = f.replace('\\', '/')
        parts = f.split('/')
        if any(p in c['ignore_dirs'] for p in parts[:-1]):
            continue
        if any(fnmatch.fnmatch(parts[-1], g) or fnmatch.fnmatch(f, g) for g in c['ignore_globs']):
            continue
        if os.path.isfile(os.path.join(ROOT, f)):
            keep.append(f)
    _FILES = sorted(set(keep))
    return _FILES

def lines_bytes(rel):
    p = os.path.join(ROOT, rel)
    if is_binary(p):
        return 0, os.path.getsize(p)
    b = open(p, 'rb').read()
    return b.count(b'\n') + (1 if b and not b.endswith(b'\n') else 0), len(b)

def kb(b):
    return '%.1f KB' % (b / 1024) if b < 1048576 else '%.2f MB' % (b / 1048576)

def is_test(rel):
    parts = rel.lower().split('/'); b = parts[-1]
    return (any(p in ('test', 'tests', '__tests__', 'spec', 'specs', 'e2e') for p in parts[:-1]) or b.startswith('test_') or
            bool(re.search(r'(_test|\.test|\.spec|_spec|test)\.[a-z]+$', b)))

_TEXT = {}
def text_of(rel):
    if rel not in _TEXT:
        p = os.path.join(ROOT, rel)
        _TEXT[rel] = '' if (is_binary(p) or os.path.getsize(p) > 1500 * 1024) else open(p, encoding='utf-8', errors='replace').read()
    return _TEXT[rel]

# ------------------------------------------------------------------ symbol index
P = re.compile
LANG = {
    'py':   [('class', P(r'^\s{0,8}class\s+([A-Za-z_]\w*)')), ('fn', P(r'^\s{0,8}(?:async\s+)?def\s+([A-Za-z_]\w*)')), ('const', P(r'^([A-Z][A-Z0-9_]{2,})\s*[:=]'))],
    'js':   [('class', P(r'^\s{0,4}(?:export\s+)?(?:default\s+)?(?:abstract\s+)?class\s+([A-Za-z_$][\w$]*)')),
             ('fn', P(r'^\s{0,4}(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s*\*?\s*([A-Za-z_$][\w$]*)')),
             ('fn', P(r'^\s{0,4}(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>')),
             ('fn', P(r'^\s{0,4}(?:window|globalThis|exports|module\.exports)\.([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?function')),
             ('const', P(r'^\s{0,4}(?:export\s+)?(?:const|let|var)\s+([A-Z][A-Z0-9_]{2,})\s*=')),
             ('type', P(r'^\s{0,4}(?:export\s+)?(?:interface|type|enum)\s+([A-Za-z_$][\w$]*)'))],
    'go':   [('fn', P(r'^func\s+(?:\([^)]*\)\s*)?([A-Za-z_]\w*)')), ('type', P(r'^type\s+([A-Za-z_]\w*)\s+(?:struct|interface)'))],
    'rs':   [('fn', P(r'^\s{0,8}(?:pub(?:\([^)]*\))?\s+)?(?:async\s+)?(?:unsafe\s+)?fn\s+([A-Za-z_]\w*)')),
             ('type', P(r'^\s{0,4}(?:pub(?:\([^)]*\))?\s+)?(?:struct|enum|trait|union)\s+([A-Za-z_]\w*)'))],
    'jvm':  [('class', P(r'^\s{0,4}(?:public\s+|private\s+|protected\s+|internal\s+|abstract\s+|final\s+|open\s+|sealed\s+|data\s+|static\s+)*(?:class|interface|enum|object|record)\s+([A-Za-z_]\w*)')),
             ('fn', P(r'^\s{0,8}(?:public|private|protected|internal)?\s*(?:static\s+)?(?:suspend\s+)?(?:fun|void|[A-Z][\w<>\[\],? ]*)\s+([a-z_]\w*)\s*\([^;]*\)\s*(?:\{|:|=|throws)'))],
    'rb':   [('class', P(r'^\s{0,6}(?:class|module)\s+([A-Z]\w*)')), ('fn', P(r'^\s{0,8}def\s+(?:self\.)?([a-z_]\w*[?!=]?)'))],
    'php':  [('class', P(r'^\s{0,4}(?:abstract\s+|final\s+)?(?:class|interface|trait)\s+([A-Za-z_]\w*)')), ('fn', P(r'^\s{0,8}(?:public\s+|private\s+|protected\s+|static\s+)*function\s+&?([A-Za-z_]\w*)'))],
    'c':    [('type', P(r'^\s{0,2}(?:typedef\s+)?(?:struct|enum|union|class|namespace)\s+([A-Za-z_]\w*)')),
             ('fn', P(r'^[A-Za-z_][\w \t\*&:<>,]*?[ \t\*&]([A-Za-z_]\w*)\s*\([^;{]*\)\s*(?:const\s*)?\{?\s*$')), ('const', P(r'^#define\s+([A-Z][A-Z0-9_]{2,})'))],
    'swift': [('class', P(r'^\s{0,4}(?:public\s+|private\s+|internal\s+|final\s+|open\s+)*(?:class|struct|enum|protocol|extension|actor)\s+([A-Za-z_]\w*)')),
              ('fn', P(r'^\s{0,8}(?:public\s+|private\s+|internal\s+|static\s+|override\s+|@\w+\s+)*func\s+([A-Za-z_]\w*)'))],
    'dart': [('class', P(r'^\s{0,2}(?:abstract\s+)?(?:class|mixin|enum|extension)\s+([A-Za-z_]\w*)')), ('fn', P(r'^\s{0,2}[A-Za-z_<>?,\[\] ]+\s+([a-zA-Z_]\w*)\s*\([^;]*\)\s*(?:async\s*)?\{'))],
    'sh':   [('fn', P(r'^\s{0,2}(?:function\s+)?([A-Za-z_][\w-]*)\s*\(\)\s*\{?'))],
    'sql':  [('fn', P(r'^\s*create\s+(?:or\s+replace\s+)?(?:table|view|function|procedure|index|trigger)\s+(?:if\s+not\s+exists\s+)?([\w."]+)', re.I))],
    'html': [('id', P(r'''\bid=["']([A-Za-z][\w:-]*)["']'''))],
    'css':  [('class', P(r'^\s*\.([A-Za-z_][\w-]*)\s*[{,:\s]'))],
}
EXT = {'.py': 'py', '.js': 'js', '.jsx': 'js', '.mjs': 'js', '.cjs': 'js', '.ts': 'js', '.tsx': 'js', '.vue': 'js', '.svelte': 'js', '.go': 'go', '.rs': 'rs',
       '.java': 'jvm', '.kt': 'jvm', '.kts': 'jvm', '.cs': 'jvm', '.scala': 'jvm', '.rb': 'rb', '.php': 'php', '.c': 'c', '.h': 'c', '.cc': 'c', '.cpp': 'c',
       '.hpp': 'c', '.swift': 'swift', '.dart': 'dart', '.sh': 'sh', '.bash': 'sh', '.sql': 'sql', '.html': 'html', '.htm': 'html', '.css': 'css', '.scss': 'css'}

def lang_of(rel):
    return EXT.get(os.path.splitext(rel)[1].lower())

def index_file(rel, c):
    lang = lang_of(rel)
    if not lang:
        return []
    p = os.path.join(ROOT, rel)
    if os.path.getsize(p) > c['max_index_file_kb'] * 1024 or is_binary(p):
        return []
    out, seen = [], set()
    for i, line in enumerate(text_of(rel).split('\n'), 1):
        if len(line) > 400:
            continue
        for kind, rx in LANG[lang]:
            m = rx.search(line)
            if m:
                name = m.group(1)
                if (name, kind) in seen and kind in ('class', 'fn'):
                    break
                seen.add((name, kind))
                out.append((name, kind, rel, i))
                break
    return out

def area_of(rel):
    parts = rel.split('/')
    if len(parts) == 1:
        return '(root)'
    if parts[0] in ('apps', 'packages', 'services', 'modules', 'libs', 'src') and len(parts) > 2:
        return '/'.join(parts[:2])
    return parts[0]

_IDX = None
def load_idx(build_if_missing=True):
    """[(name, kind, rel, line)] from symbols.idx (rebuilt when absent)."""
    global _IDX
    if _IDX is not None:
        return _IDX
    p = os.path.join(ROOT, 'symbols.idx')
    if not os.path.exists(p) and build_if_missing:
        cmd_index(quiet=True)
    rows = []
    if os.path.exists(p):
        for ln in open(p, encoding='utf-8'):
            if ln.startswith('#') or '\t' not in ln:
                continue
            a = ln.rstrip('\n').split('\t')
            if len(a) >= 3 and ':' in a[2]:
                rel, _, num = a[2].rpartition(':')
                rows.append((a[0], a[1], rel, int(num) if num.isdigit() else 0))
    _IDX = rows
    return rows

def cmd_index(c=None, quiet=False):
    global _IDX
    c = c or cfg()
    files = project_files(c)
    rows, by_area = [], {}
    for rel in files:
        n, b = lines_bytes(rel)
        a = by_area.setdefault(area_of(rel), [0, 0, 0]); a[0] += 1; a[1] += n; a[2] += b
        if any(fnmatch.fnmatch(rel, g) for g in c['index_ignore_globs']):
            continue
        rows += index_file(rel, c)
    lines = ['# AUTO-GENERATED by brain/brain.py - do not edit. Format: name<TAB>kind<TAB>file:line',
             '# Use: python3 brain/brain.py find NAME   (never read this file whole)']
    lines += ['%s\t%s\t%s:%d' % r for r in sorted(rows, key=lambda r: (r[0].lower(), r[2], r[3]))]
    write('symbols.idx', '\n'.join(lines) + '\n')
    _IDX = None
    top = sorted(by_area.items(), key=lambda kv: -kv[1][1])[:14]
    auto = ['<!-- AUTO:START (rewritten by `python3 brain/brain.py index`; keep this block small) -->',
            '_%s: %d files, %s lines, %d symbols. Biggest areas:_' % (today(), len(files), format(sum(v[1] for v in by_area.values()), ','), len(rows)), '',
            '| Area | Files | Lines |', '|---|---:|---:|'] + ['| `%s` | %d | %s |' % (a, v[0], format(v[1], ',')) for a, v in top]
    auto.append('<!-- AUTO:END -->')
    block = '\n'.join(auto)
    g = read('AI_GUIDE.md') or '# AI_GUIDE.md\n\n## 8. Auto-generated map (do not edit)\n<!-- AUTO:START -->\n<!-- AUTO:END -->\n'
    if 'AUTO:START' in g:
        g = re.sub(r'<!-- AUTO:START.*?<!-- AUTO:END -->', lambda m: block, g, flags=re.S)
    else:
        g = g.rstrip('\n') + '\n\n## 8. Auto-generated map (do not edit)\n' + block + '\n'
    write('AI_GUIDE.md', g)
    if not quiet:
        print('index: %d files, %d symbols -> symbols.idx (+ small stats block in AI_GUIDE.md)' % (len(files), len(rows)))

# ------------------------------------------------------------------ find / outline / show
def cmd_find(args):
    if not args:
        print('usage: find NAME'); return 2
    q = args[0].lower(); rows = load_idx()
    ex = [r for r in rows if r[0].lower() == q]
    pre = [r for r in rows if r[0].lower().startswith(q) and r not in ex]
    sub = [r for r in rows if len(q) > 3 and q in r[0].lower() and r not in ex and r not in pre]
    hits = (ex + pre + sub)[:30]
    for n, k, rel, ln in hits:
        print('%s\t%s\t%s:%d' % (n, k, rel, ln))
    if not hits:
        print('no symbol like "%s". Try: ctx "words about it"  (finds by meaning), or grep inside one folder.' % args[0])
    elif len(ex + pre + sub) > 30:
        print('... more; use a longer name')
    return 0

def cmd_outline(args):
    if not args:
        print('usage: outline FILE'); return 2
    rel = args[0]
    cands = [f for f in project_files() if f == rel or f.endswith('/' + rel)]
    if not cands:
        print('no such file in the project: %s' % rel); return 1
    rel = cands[0]
    n, b = lines_bytes(rel)
    purpose = parse_structure().get(rel, '')
    print('%s  (%d lines, ~%d tokens)%s' % (rel, n, est(text_of(rel)), ('  - ' + purpose) if purpose else ''))
    syms = sorted([r for r in load_idx() if r[2] == rel], key=lambda r: r[3])
    for name, kind, _, ln in syms[:200]:
        print('%5d %-5s %s' % (ln, kind, name))
    if not syms:
        print('(no indexed symbols; use: show/Read with offset)')
    return 0

def body_range(lines, i0, lang, maxn):
    n = len(lines)
    start = i0
    while start > 0 and lines[start - 1].lstrip().startswith(('@', '#[')) and i0 - start < 4:
        start -= 1
    first = lines[i0]
    if lang in ('py',) or (lang == 'rb' and not first.rstrip().endswith('end')):
        ind = len(first) - len(first.lstrip())
        if lang == 'rb':
            j = i0 + 1
            while j < n and not (lines[j].strip() == 'end' and len(lines[j]) - len(lines[j].lstrip()) == ind) and j - i0 < 400:
                j += 1
            return start, min(j, n - 1)
        j = i0 + 1; last = i0
        while j < n and j - i0 < 400:
            if lines[j].strip():
                if len(lines[j]) - len(lines[j].lstrip()) <= ind and not lines[j].lstrip().startswith((')', ']', '}')):
                    break
                last = j
            j += 1
        return start, last
    depth, seen = 0, False
    for j in range(i0, min(n, i0 + 500)):
        s = re.sub(r'''("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)''', '', lines[j])
        s = re.sub(r'//.*$', '', s) if lang != 'sh' else re.sub(r'\s#.*$', '', s)
        depth += s.count('{') - s.count('}')
        if '{' in s:
            seen = True
        if seen and depth <= 0:
            return start, j
        if not seen and j - i0 <= 3 and s.rstrip().endswith(';'):
            return start, j
        if not seen and j - i0 >= 3 and not s.strip():
            return start, j - 1
    return start, min(n - 1, i0 + maxn)

def cmd_show(args):
    if not args:
        print('usage: show NAME [--lines N] [--file PATH]'); return 2
    maxn = 80
    if '--lines' in args:
        maxn = int(args[args.index('--lines') + 1]);
    want_file = args[args.index('--file') + 1] if '--file' in args else None
    name = args[0]
    rows = [r for r in load_idx() if r[0] == name and (not want_file or r[2].endswith(want_file))] or \
           [r for r in load_idx() if r[0].lower() == name.lower() and (not want_file or r[2].endswith(want_file))]
    if not rows:
        print('no symbol "%s"; try: find %s' % (name, name[:6])); return 1
    if len(rows) > 1:
        print('%d definitions; showing the first. Others: %s' % (len(rows), ', '.join('%s:%d' % (r[2], r[3]) for r in rows[1:6])))
    _, kind, rel, ln = rows[0]
    lines = text_of(rel).split('\n')
    a, b = body_range(lines, ln - 1, lang_of(rel) or '', maxn)
    seg = lines[a:b + 1]
    shown = seg[:maxn]
    print('%s:%d-%d (%d lines)' % (rel, a + 1, b + 1, len(seg)))
    print('\n'.join(shown))
    if len(seg) > maxn:
        print('... +%d more lines (show %s --lines %d)' % (len(seg) - maxn, name, len(seg)))
    return 0

# ------------------------------------------------------------------ STRUCTURE / map / guide
LISTED = re.compile(r'^- `([^`]+)` — (.*)$', re.M)

def parse_structure():
    return {m.group(1): m.group(2) for m in LISTED.finditer(read('STRUCTURE.md'))}

def first_comment(rel):
    p = os.path.join(ROOT, rel)
    if is_binary(p) or os.path.getsize(p) > 2_000_000:
        return ''
    txt = open(p, encoding='utf-8', errors='replace').read(3000)
    pats = [r'^\s*(?:"""|\'\'\')\s*(.+?)\s*(?:"""|\'\'\'|$)', r'^\s*/\*+\s*[=*-]*\s*\n?\s*\*?\s*(.+?)\s*(?:\*/|$)', r'^\s*(?://|#|--)\s*(?!!)(.{12,})$',
            r'^\s*<!--\s*(.+?)\s*(?:-->|$)', r'^#\s+(.+)$']
    for line in txt.split('\n')[:25]:
        s = line.strip()
        if not s or s.startswith(('#!', '<?', '<!doctype', '<!DOCTYPE', 'import ', 'from ', 'use ', 'package ', '"use strict', "'use strict")):
            continue
        for rx in pats:
            m = re.match(rx, line)
            if m and len(m.group(1).strip(' =*-/#')) >= 8:
                return m.group(1).strip(' =*-/#"\'')[:160]
    m = re.search(r'<title>\s*(.+?)\s*</title>', txt, re.I | re.S)
    return m.group(1).strip()[:160] if m else ''

def guess_purpose(rel):
    d = first_comment(rel)
    if d:
        return d
    base = re.sub(r'^\d+[-_.]', '', os.path.splitext(os.path.basename(rel))[0]).replace('-', ' ').replace('_', ' ')
    return ('%s (describe me)' % base).strip()

def cmd_structure(args):
    c = cfg()
    actual = [f for f in project_files(c) if not any(fnmatch.fnmatch(f, g) for g in c['structure_ignore'])]
    listed = parse_structure()
    missing = [f for f in actual if f not in listed]
    ghost = [p for p in listed if not os.path.exists(os.path.join(ROOT, p)) and not p.endswith('/')]
    if '--draft' in args and missing:
        txt = read('STRUCTURE.md') or '# STRUCTURE.md - every file and what it does\n\n'
        add = ['', '### Added %s (replace every "describe me")' % today()] + ['- `%s` — %s' % (f, guess_purpose(f)) for f in missing]
        write('STRUCTURE.md', txt.rstrip('\n') + '\n' + '\n'.join(add) + '\n')
        print('structure: added %d files to STRUCTURE.md (edit the "describe me" lines)' % len(missing))
        missing = []; listed = parse_structure()
    for f in missing[:40]:
        print('NOT IN STRUCTURE.md:', f)
    if len(missing) > 40:
        print('... and %d more' % (len(missing) - 40))
    for g in ghost[:40]:
        print('IN STRUCTURE.md BUT MISSING ON DISK:', g)
    if missing or ghost:
        print('-> `python3 brain/brain.py structure --draft` adds missing files; delete lines for removed files.')
        return 1
    print('structure: OK (%d files)' % len(listed))
    return 0

def cmd_map(args):
    st = parse_structure()
    if not st:
        print('STRUCTURE.md has no entries yet: run `structure --draft`'); return 1
    if not args:
        cnt = {}
        for p in st:
            k = p.split('/')[0] if '/' in p else '(root)'
            cnt[k] = cnt.get(k, 0) + 1
        print('folders (use: map DIR):')
        for k, v in sorted(cnt.items(), key=lambda kv: -kv[1]):
            print('  %-28s %d files' % (k, v))
        return 0
    d = args[0].strip('/')
    hits = [(p, s) for p, s in st.items() if p.startswith(d + '/') or p == d or d in p.split('/')[-1]]
    for p, s in hits[:150]:
        print('%s - %s' % (p, s[:140]))
    if len(hits) > 150:
        print('... %d more; use a deeper folder' % (len(hits) - 150))
    if not hits:
        print('nothing under "%s"' % d)
    return 0

def guide_sections():
    g = read('AI_GUIDE.md')
    parts = re.split(r'^(?=## )', g, flags=re.M)
    return [(p.split('\n', 1)[0].lstrip('# ').strip(), p) for p in parts if p.startswith('## ')]

def cmd_guide(args):
    secs = guide_sections()
    if not secs:
        print('AI_GUIDE.md missing or has no "## " sections'); return 1
    if not args:
        for t, body in secs:
            print('%-70s ~%d tokens' % (t[:70], est(body)))
        print('read one: guide <number or word>   (e.g. guide 0a, guide router)')
        return 0
    q = args[0].lower()
    for t, body in secs:
        if t.lower().startswith(q) or q in t.lower():
            print(body.rstrip()); return 0
    print('no section matching "%s"' % args[0]); return 1

# ------------------------------------------------------------------ retrieval (ctx)
STOP = set('a an the of to in on for and or with is are be do does how what when where why which that this it its at by from as into can should would '
           'i me my we our you your add make fix change update create use using get set new old all any need want please code file files function'.split())

def stem(w):
    for suf in ('ing', 'ed', 'es', 's', 'ly'):
        if w.endswith(suf) and len(w) - len(suf) >= 3:
            return w[:-len(suf)]
    return w

def toks(s, keep_stop=False):
    out = []
    for m in re.findall(r'[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\d+', s.replace('_', ' ').replace('-', ' ')):
        w = m.lower()
        if len(w) < 2 or (w in STOP and not keep_stop):
            continue
        out.append(stem(w))
    return out

def parse_lessons():
    out = []
    for i, ln in enumerate(read('docs/LESSONS.md').split('\n'), 1):
        m = re.match(r'^- \[(\d{4}-\d{2}-\d{2})\]\s*(.*?)(?:\s+—\s+refs:\s*(.*))?$', ln)
        if m:
            out.append({'line': i, 'date': m.group(1), 'text': m.group(2).strip(), 'refs': [r.strip() for r in (m.group(3) or '').split(',') if r.strip()]})
    return out

def router_rows():
    rows = []
    for t, body in guide_sections():
        if 'router' in t.lower():
            for ln in body.split('\n'):
                if ln.startswith('|') and not ln.startswith('|---') and 'example:' not in ln.lower():
                    cells = [x.strip() for x in ln.strip('|').split('|')]
                    if len(cells) >= 2 and cells[0].lower() not in ('the request is about', 'user talks about'):
                        rows.append(ln)
    return rows

def tests_for_file(rel):
    stem_ = os.path.splitext(os.path.basename(rel))[0]
    stem_ = re.sub(r'^\d+[-_.]', '', stem_)
    bare = re.sub(r'[-_]', '', stem_).lower()
    syms = [r[0] for r in load_idx() if r[2] == rel and len(r[0]) > 5 and r[1] in ('fn', 'class')][:12]
    out = []
    for f in project_files():
        if not is_test(f) or f == rel:
            continue
        bf = re.sub(r'[-_.]', '', os.path.basename(f).lower())
        score = 0
        if bare and bare in bf:
            score += 3
        t = text_of(f)
        if stem_ and re.search(r'(?<![\w])%s(?![\w])' % re.escape(stem_), t):
            score += 2
        score += sum(1 for s in syms if s in t)
        if score >= 2:
            out.append((score, f))
    return [f for _, f in sorted(out, key=lambda x: (-x[0], x[1]))][:6]

def build_docs():
    st = parse_structure(); idx = load_idx(); docs = {}
    by_file = {}
    for n, k, rel, ln in idx:
        by_file.setdefault(rel, []).append((n, ln))
    for rel in project_files():
        if rel in ('symbols.idx',) or rel.startswith(('brain/', '.claude/')):
            continue
        w = {}
        def add(ts, wt):
            for t in ts:
                w[t] = w.get(t, 0) + wt
        parts = rel.split('/')
        add(toks('/'.join(parts[:-1])), 1.0); add(toks(parts[-1]), 3.0)
        add(toks(st.get(rel, '')), 2.0)
        for n, ln in by_file.get(rel, [])[:300]:
            add(toks(n), 1.5)
        docs[rel] = w
    for row in router_rows():
        rt = toks(row)
        for m in re.findall(r'`([^`]+)`', row):
            for rel in docs:
                if m.endswith(rel.split('/')[-1]) or rel.endswith(m.strip('/')):
                    for t in rt:
                        docs[rel][t] = docs[rel].get(t, 0) + 2.0
    return docs, by_file

def cmd_ctx(args):
    mx = 1500
    if '--max' in args:
        i = args.index('--max'); mx = int(args[i + 1]); args = args[:i] + args[i + 2:]
    task = ' '.join(args).strip()
    if not task:
        print('usage: ctx "what you are about to do"  [--max TOKENS]'); return 2
    q = list(dict.fromkeys(toks(task)))
    if not q:
        print('no usable words in the task'); return 2
    docs, by_file = build_docs()
    N = max(1, len(docs)); df = {}
    for w in docs.values():
        for t in w:
            df[t] = df.get(t, 0) + 1
    st = parse_structure()
    scored = []
    for rel, w in docs.items():
        s, hit = 0.0, 0
        for t in q:
            tf = w.get(t, 0)
            if tf:
                hit += 1
                s += math.log(1 + N / (1 + df.get(t, 0))) * (tf / (tf + 1.5))
        if hit:
            s *= 0.55 + 0.45 * hit / len(q)
            if is_test(rel):
                s *= 0.8
            if re.search(r'(?i)\bgenerated\b|do not (hand-)?edit|never hand-edit', st.get(rel, '')[:200]) or rel.endswith(('.bundle.html', '.min.js')):
                s *= 0.1
            scored.append((s, rel))
    if not scored:      # nothing matched: retry with the little words too (add, make, get...)
        q = list(dict.fromkeys(toks(task, keep_stop=True)))
        for rel, w in docs.items():
            hit = [t for t in q if w.get(t)]
            if hit:
                scored.append((sum(math.log(1 + N / (1 + df.get(t, 0))) for t in hit) / len(q), rel))
    scored.sort(key=lambda x: (-x[0], x[1]))
    top = [r for _, r in scored[:8]]
    st = parse_structure()
    out = ['ctx: "%s"   words: %s' % (task[:80], ' '.join(q[:10]))]
    if not top:
        out.append('nothing matched. Try other words, `map` (folders), or `find NAME`.')
    if top:
        out.append('FILES (best first)')
    for i, rel in enumerate(top, 1):
        syms = [(n, ln) for n, ln in by_file.get(rel, []) if set(toks(n)) & set(q)][:4]
        line = '%d. %s' % (i, rel)
        if st.get(rel):
            line += ' - ' + st[rel][:100]
        out.append(line)
        if syms:
            out.append('   symbols: ' + ', '.join('%s:%d' % s for s in syms))
    tests = []
    for rel in top[:4]:
        if not is_test(rel):
            tests += [t for t in tests_for_file(rel) if t not in tests and t not in top]
    if tests:
        out.append('TESTS likely relevant: ' + ', '.join(tests[:5]))
    ls = []
    for ls_ in parse_lessons():
        sc = len(set(toks(ls_['text'])) & set(q)) + sum(2 for r in ls_['refs'] if any(r == t or r.endswith(t) for t in top))
        if sc >= 2:
            ls.append((sc, ls_))
    if ls:
        out.append('LESSONS')
        for _, l in sorted(ls, key=lambda x: -x[0])[:4]:
            out.append('- %s' % l['text'][:200])
    rr = [(len(set(toks(r)) & set(q)), r) for r in router_rows()]
    rr = [r for r in sorted(rr, key=lambda x: -x[0]) if r[0] >= 2][:2]
    if rr:
        out.append('ROUTER')
        out += [r[1][:240] for r in rr]
    out.append('NEXT: outline FILE | show SYMBOL | impact SYMBOL | tests-for FILE')
    text, total = [], 0
    for ln in out:
        t = est(ln) + 1
        if total + t > mx:
            text.append('... (trimmed to ~%d tokens; --max for more)' % mx); break
        text.append(ln); total += t
    print('\n'.join(text))
    return 0

# ------------------------------------------------------------------ impact / tests-for
IMPORT_RX = re.compile(r'^\s*(?:import\b|from\b.+\bimport\b|(?:const|let|var)\s+.*=\s*require\(|#include\b|use\s|using\s|require(?:_relative)?\b|@import)')

def cmd_impact(args):
    if not args:
        print('usage: impact NAME'); return 2
    name = args[0]
    defs = [r for r in load_idx() if r[0] == name]
    stem_hit = [f for f in project_files() if os.path.splitext(os.path.basename(f))[0] == name]
    rx = re.compile(r'(?<![\w$])%s(?![\w$])' % re.escape(name))
    defset = {(r[2], r[3]) for r in defs}
    per = {}
    for f in project_files():
        if lang_of(f) is None:
            continue
        if f.startswith(('brain/', '.claude/')) or f == 'symbols.idx':
            continue
        for i, ln in enumerate(text_of(f).split('\n'), 1):
            if len(ln) > 600 or not rx.search(ln):
                continue
            if (f, i) in defset:
                continue
            kind = 'import' if IMPORT_RX.match(ln) else ('call' if re.search(r'(?<![\w$])%s\s*\(' % re.escape(name), ln) else 'ref')
            per.setdefault(f, []).append((i, kind))
    files = sorted(per)
    tests = [f for f in files if is_test(f)]
    srcs = [f for f in files if not is_test(f)]
    calls = sum(1 for f in srcs for _, k in per[f] if k == 'call')
    imps = sorted({f for f in srcs for _, k in per[f] if k == 'import'})
    radius = 'HIGH' if len(srcs) > 12 else ('MEDIUM' if len(srcs) > 3 else 'LOW')
    print('%s: %s' % (name, ('defined ' + ', '.join('%s:%d' % (r[2], r[3]) for r in defs[:3])) if defs else 'no definition in the index (external or a plain word?)'))
    print('blast radius: %s - %d source files (%d call sites, %d import lines), %d test files' % (radius, len(srcs), calls, len(imps), len(tests)))
    if len(name) < 4:
        print('note: short name, matches may include unrelated words')
    for f in srcs[:30]:
        locs = per[f]
        print('  %s  %s' % (f, ' '.join('%d%s' % (n, {'call': '', 'import': 'i', 'ref': 'r'}[k]) for n, k in locs[:8]) + (' +%d' % (len(locs) - 8) if len(locs) > 8 else '')))
    if len(srcs) > 30:
        print('  ... +%d more files' % (len(srcs) - 30))
    if tests:
        print('tests that mention it: ' + ', '.join(tests[:8]))
    if stem_hit:
        who = []
        st_ = os.path.splitext(os.path.basename(stem_hit[0]))[0]
        for f in project_files():
            if f != stem_hit[0] and lang_of(f) and re.search(r'(?:import|from|require|include|use)[^\n]{0,80}[\'"/. ]%s[\'".; /]' % re.escape(st_), text_of(f)):
                who.append(f)
        if who:
            print('files importing module "%s": %s' % (st_, ', '.join(who[:12]) + (' +%d' % (len(who) - 12) if len(who) > 12 else '')))
    print('legend: 12=call 12i=import 12r=mention')
    return 0

def test_cmd_for(c, t):
    tpl = c.get('test_cmd') or ''
    if not tpl:
        if t.endswith('.py'):
            tpl = 'python3 -m pytest -q {test}' if 'pytest' in read('requirements.txt') + read('pyproject.toml') else 'python3 {test}'
        elif t.endswith(('.js', '.mjs', '.cjs')):
            tpl = 'node {test}'
        elif t.endswith('.go'):
            tpl = 'go test ./%s/...' % os.path.dirname(t)
        else:
            tpl = ''
    return tpl.replace('{test}', t) if tpl else ''

def cmd_tests_for(args):
    if not args:
        print('usage: tests-for FILE|SYMBOL'); return 2
    c = cfg(); a = args[0]
    cands = [f for f in project_files() if f == a or f.endswith('/' + a)]
    if cands:
        ts = tests_for_file(cands[0])
    else:
        rx = re.compile(r'(?<![\w$])%s(?![\w$])' % re.escape(a))
        ts = [f for f in project_files() if is_test(f) and rx.search(text_of(f))][:8]
    if not ts:
        print('no test mentions %s. Write one, or run the full suite.' % a); return 0
    for t in ts:
        print('%s    %s' % (t, test_cmd_for(c, t)))
    return 0

# ------------------------------------------------------------------ run (compressed output)
ANSI = re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]|\x1b\][^\x07]*\x07')
BAD = re.compile(r'(?i)\b(fail(ed|ure|ing)?|error|exception|traceback|panic|fatal|assert(ion)?|denied|cannot|can\'t|not found|undefined|unresolved|timeout|segfault|abort|✗|✖|×)\b|^E\s{2,}|^\s*>\s|FAILED|npm ERR|error\[E\d+\]|error TS\d+|warning:')
SUMMARY = re.compile(r'(?i)(\d+\s+(passed|failed|passing|failing|errors?|warnings?|skipped|tests?)\b|^tests?:|^ran \d+|^=+ .* =+$|^test result|^ok\s+\S+\s+[\d.]+s|^build (succeeded|failed)|^done in|^finished|^real\s)')
PASSY = re.compile(r'(?i)^\s*(ok\b|pass(ed)?\b|✓|✔|\.+\s*$|\[?\s*ok\s*\]?|-+\s*$|running |compiling |downloading |installing |added \d+ packages|up to date)')

def compress(text, rc, maxn=30):
    text = ANSI.sub('', text)
    raw = [l.split('\r')[-1].rstrip() for l in text.replace('\r\n', '\n').split('\n')]
    raw = [l for l in raw if l.strip()]
    n = len(raw)
    keep = set()
    i = 0
    while i < n:
        l = raw[i]
        if l.startswith('Traceback'):
            j = i
            while j + 1 < n and (raw[j + 1].startswith((' ', '\t')) or not re.match(r'^\w*(Error|Exception)\b', raw[j])) and j - i < 40:
                j += 1
                if re.match(r'^[\w.]*(Error|Exception|Exit|Interrupt)\b', raw[j]):
                    break
            keep.update(range(i, min(n, j + 1))); i = j + 1; continue
        if BAD.search(l) and not PASSY.match(l):
            keep.update(range(max(0, i - 1), min(n, i + 3)))
        elif SUMMARY.search(l):
            keep.add(i)
        i += 1
    keep.update(range(max(0, n - 3), n))
    if rc == 0 and not any(BAD.search(raw[k]) and not PASSY.match(raw[k]) for k in range(n)):
        keep = set(range(max(0, n - 3), n)) | {k for k in range(n) if SUMMARY.search(raw[k])}
    out, prev, cnt = [], None, 0
    for k in sorted(keep):
        l = raw[k][:220]
        if l == prev:
            cnt += 1; continue
        if cnt:
            out[-1] += '  (x%d)' % (cnt + 1)
        prev, cnt = l, 0
        out.append(l)
    if cnt:
        out[-1] += '  (x%d)' % (cnt + 1)
    if len(out) > maxn:
        out = out[:maxn - 8] + ['... (%d more kept lines hidden)' % (len(out) - maxn + 2)] + out[-6:]
    return out, n

def cmd_run(args):
    maxn = 30
    if '--max' in args:
        i = args.index('--max'); maxn = int(args[i + 1]); args = args[:i] + args[i + 2:]
    cmd = ' '.join(args).strip()
    if not cmd:
        print('usage: run "command" [--max N]'); return 2
    rc, out, err = sh(cmd)
    full = out + ('\n' + err if err else '')
    os.makedirs(CACHE, exist_ok=True)
    open(os.path.join(CACHE, 'last_run.log'), 'w', encoding='utf-8').write('$ %s\nexit=%d\n%s\n' % (cmd, rc, full))
    lines, n = compress(full, rc, maxn)
    print('exit=%d  %d lines -> %d shown  (full: brain/.cache/last_run.log; grep it)' % (rc, n, len(lines)))
    print('\n'.join(lines))
    return rc

# ------------------------------------------------------------------ diff
def cmd_diff(args):
    base = 'HEAD'
    rc, out, _ = sh('git diff --numstat %s' % base)
    if rc != 0:
        print('not a git repo or no commits'); return 1
    st = parse_structure(); rows = []
    for ln in out.split('\n'):
        a = ln.split('\t')
        if len(a) == 3:
            rows.append((a[2], a[0], a[1]))
    _, un, _ = sh('git ls-files --others --exclude-standard')
    for f in un.split('\n'):
        if f and f not in [r[0] for r in rows]:
            rows.append((f, '+new', ''))
    ign = cfg()['structure_ignore']
    hidden = [r for r in rows if any(fnmatch.fnmatch(r[0], g) for g in ign)]
    rows = [r for r in rows if r not in hidden]
    if not rows and not hidden:
        print('no changes vs HEAD'); return 0
    print('%d files changed%s' % (len(rows), (' (+%d brain/generated files hidden)' % len(hidden)) if hidden else ''))
    for f, a, d in rows[:60]:
        print('%-8s %s  %s' % (('+%s -%s' % (a, d)) if d != '' else a, f, ('- ' + st[f][:70]) if f in st else ''))
    if '--hunks' in args:
        rc, hunks, _ = sh('git diff -U0 %s' % base)
        cur = None; shown = 0
        for ln in hunks.split('\n'):
            if ln.startswith('+++ b/'):
                cur = ln[6:]
            elif ln.startswith('@@') and shown < 80:
                print('  %s %s' % (cur, ln[:110])); shown += 1
    new = [f for f, a, d in rows if a == '+new' and f not in st and not any(fnmatch.fnmatch(f, g) for g in cfg()['structure_ignore'])]
    if new:
        print('NOT YET IN STRUCTURE.md: ' + ', '.join(new[:10]))
    return 0

# ------------------------------------------------------------------ lessons / audit
def cmd_learn(args):
    refs = ''
    if '--ref' in args:
        i = args.index('--ref'); refs = args[i + 1]; args = args[:i] + args[i + 2:]
    text = ' '.join(args).strip()
    if not text:
        print('usage: learn "what cost you time and the fix" [--ref path,symbol]'); return 2
    p = 'docs/LESSONS.md'
    head = '# LESSONS (each cost time once). One line each; `audit` flags lines whose files or symbols changed or vanished.\nFormat: `- [date] lesson - refs: path, symbol`\n\n'
    cur = read(p) or head
    if text.lower() in cur.lower():
        print('lesson already recorded'); return 0
    line = '- [%s] %s%s' % (today(), text, (' — refs: ' + ', '.join(r.strip() for r in refs.split(',') if r.strip())) if refs else '')
    write(p, cur.rstrip('\n') + '\n' + line + '\n')
    print('learned: ' + line[:160])
    return 0

def git_date(rel):
    rc, out, _ = sh('git log -1 --format=%%cs -- "%s"' % rel)
    return out if rc == 0 else ''

def cmd_audit(args):
    quiet = '--quiet' in args
    idx_names = {r[0] for r in load_idx()}; files = set(project_files())
    E, W, I = [], [], []
    ls = parse_lessons()
    for l in ls:
        for r in l['refs']:
            if '/' in r or re.search(r'\.\w{1,5}$', r):
                if r not in files:
                    E.append('lesson (line %d) points to a file that no longer exists: %s' % (l['line'], r))
                else:
                    d = git_date(r)
                    if d and d > l['date']:
                        W.append('lesson (line %d) is older than the last change of %s (%s > %s): re-verify' % (l['line'], r, d, l['date']))
            elif r not in idx_names:
                E.append('lesson (line %d) points to a symbol that is gone: %s' % (l['line'], r))
    neg = re.compile(r'(?i)\b(never|don\'t|do not|avoid|must not|not use)\b'); pos = re.compile(r'(?i)\b(always|must|should|use)\b')
    for i in range(len(ls)):
        for j in range(i + 1, len(ls)):
            a, b = ls[i], ls[j]
            share = set(toks(a['text'])) & set(toks(b['text']))
            if (set(a['refs']) & set(b['refs']) or len(share) >= 4) and bool(neg.search(a['text'])) != bool(neg.search(b['text'])) and len(share) >= 3:
                W.append('lessons on lines %d and %d may contradict each other (share: %s)' % (a['line'], b['line'], ', '.join(sorted(share)[:5])))
    seen = {}
    for l in ls:
        k = re.sub(r'\W+', ' ', l['text'].lower()).strip()
        if k in seen:
            W.append('lessons on lines %d and %d are duplicates' % (seen[k], l['line']))
        seen[k] = l['line']
    g = read('AI_GUIDE.md')
    if not g:
        E.append('AI_GUIDE.md is missing')
    else:
        for m in sorted(set(re.findall(r'`((?:[\w.-]+/)+[\w.-]+\.\w{1,5})`', g))):
            base = m.rsplit('/', 1)[-1]
            if m not in files and base not in {f.rsplit('/', 1)[-1] for f in files} and not any(fnmatch.fnmatch(m, x) for x in cfg()['structure_ignore']) and not m.startswith(('http', 'brain/', 'docs/<')) and '<' not in m and '*' not in m:
                W.append('AI_GUIDE.md mentions a path that does not exist: %s' % m)
        for row in router_rows():
            for m in re.findall(r'`([A-Za-z_$][\w$.]{3,})`', row):
                if '/' not in m and '.' not in m and m not in idx_names and not re.search(r'\.\w{1,4}$', m):
                    W.append('router row mentions an unknown symbol: %s' % m)
        if 'FILL IN' in g or 'Project in 10 lines' in g and re.search(r'Project in 10 lines[^\n]*\n(?:- [^\n]*:\s*\n){3,}', g):
            W.append('AI_GUIDE.md still has unfilled sections (FILL IN / empty bullets): the brain is only as good as these')
        for t, body in guide_sections():
            if t.startswith('7') and len([x for x in body.split('\n') if x.startswith('- ')]) > 30:
                W.append('changelog (section 7) has over 30 lines: merge the old ones')
    st = parse_structure()
    dm = sum(1 for s in st.values() if 'describe me' in s)
    if dm:
        I.append('%d STRUCTURE.md lines still say "describe me"' % dm)
    for f in sorted(files):
        if f in st or is_binary(os.path.join(ROOT, f)):
            continue
    import glob
    for p in glob.glob(os.path.join(ROOT, cfg()['plans_glob'])):
        age = (datetime.datetime.now().timestamp() - os.path.getmtime(p)) / 86400
        t = open(p, encoding='utf-8', errors='replace').read()
        if age > 45 and re.search(r'^\s*- \[ \]', t, re.M) and 'TEMPLATE' not in p:
            W.append('plan %s has open steps but was not touched for %d days' % (os.path.relpath(p, ROOT), age))
    gen = {f for f, d in st.items() if re.search(r'(?i)\bgenerated\b', d[:200])}
    big = sorted(((est(text_of(f)), f) for f in files if not is_test(f) and f not in gen and f != 'symbols.idx' and not f.endswith('.bundle.html')), reverse=True)[:3]
    for t, f in big:
        if t > 15000:
            I.append('%s is ~%dk tokens: use outline/show, never read it whole' % (f, t // 1000))
    if not quiet or E:
        for lab, arr in (('ERROR', E), ('WARN', W), ('INFO', I)):
            for m in arr[:25]:
                print('%s  %s' % (lab, m))
        print('audit: %d errors, %d warnings, %d notes' % (len(E), len(W), len(I)))
    return 1 if E else 0

# ------------------------------------------------------------------ secrets
SECRET_PATTERNS = [('GitHub token', r'gh[pousr]_[A-Za-z0-9]{30,}'), ('GitHub fine-grained token', r'github_pat_[A-Za-z0-9_]{40,}'), ('AWS access key', r'\bAKIA[0-9A-Z]{16}\b'),
                   ('Private key block', r'-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----'), ('API key (sk-)', r'\bsk-[A-Za-z0-9_-]{20,}'),
                   ('Slack token', r'\bxox[baprs]-[A-Za-z0-9-]{10,}'), ('Google API key', r'\bAIza[0-9A-Za-z_-]{35}\b'), ('Stripe live key', r'\b[sr]k_live_[0-9A-Za-z]{16,}'),
                   ('Razorpay live key', r'\brzp_live_[0-9A-Za-z]{10,}'), ('JWT', r'\beyJ[A-Za-z0-9_-]{15,}\.eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}'),
                   ('Hard-coded secret', r'''(?i)\b(?:secret|passwd|password|api[_-]?key|access[_-]?token|auth[_-]?token)\b['"]?\s*[:=]\s*['"][A-Za-z0-9/+_=.-]{16,}['"]''')]

def cmd_secrets(args):
    scan = []
    if '--all' in args:
        scan = [(f, i, l) for f in project_files() if not f.startswith('brain/') for i, l in enumerate(text_of(f).split('\n'), 1)]
    else:
        rc, d, _ = sh('git diff --cached -U0')
        cur = None; ln = 0
        for l in d.split('\n'):
            if l.startswith('+++ b/'):
                cur = l[6:]
            elif l.startswith('@@'):
                m = re.search(r'\+(\d+)', l); ln = int(m.group(1)) - 1 if m else 0
            elif l.startswith('+') and not l.startswith('+++'):
                ln += 1; scan.append((cur, ln, l[1:]))
    hits = []
    for f, i, l in scan:
        if 'brain:allow-secret' in l or len(l) > 2000:
            continue
        for name, rx in SECRET_PATTERNS:
            m = re.search(rx, l)
            if m:
                v = m.group(0); hits.append((name, f, i, v[:6] + '…' + v[-2:])); break
    for name, f, i, v in hits[:30]:
        print('SECRET? %s  %s:%d  %s' % (name, f, i, v))
    print('secrets: %s' % ('%d suspicious lines (add `brain:allow-secret` on a line to whitelist it)' % len(hits) if hits else 'none found'))
    return 1 if hits else 0

# ------------------------------------------------------------------ handoff / start / brief
def plan_next():
    import glob
    out = []
    for p in sorted(glob.glob(os.path.join(ROOT, cfg()['plans_glob']))):
        if 'TEMPLATE' in p:
            continue
        for ln in open(p, encoding='utf-8', errors='replace'):
            if re.match(r'^\s*- \[( |~)\]', ln):
                out.append((os.path.relpath(p, ROOT), ln.strip()[:170])); break
    return out

def git_state(c):
    _, br, _ = sh('git rev-parse --abbrev-ref HEAD'); _, head, _ = sh('git rev-parse --short HEAD')
    sh('git fetch -q origin', timeout=25)
    rc, behind, _ = sh('git rev-list --count HEAD..origin/%s' % c['main_branch'])
    _, st, _ = sh('git status --short')
    return br, head, (behind if rc == 0 else None), len([x for x in st.split('\n') if x])

def cmd_start(quiet=False):
    c = cfg(); br, head, behind, nchg = git_state(c)
    out = ['git: %s @ %s | behind origin/%s: %s | working tree: %s' % (br or '?', head or '?', c['main_branch'], behind if behind is not None else 'unknown', ('%d changed files' % nchg) if nchg else 'clean')]
    if behind not in (None, '0', ''):
        out.append('!! BEHIND: git fetch origin %s && git rebase origin/%s  BEFORE you change anything' % (c['main_branch'], c['main_branch']))
    for p, ln in plan_next():
        out.append('plan [%s] next: %s' % (p, ln))
    h = read('docs/HANDOFF.md')
    if h:
        out.append('handoff (docs/HANDOFF.md):')
        keep = []
        for x in h.strip().split('\n'):
            if x.startswith(('Branch:', 'Last commits')):
                break
            keep.append(x)
        out += ['  ' + x for x in keep[:16] if x.strip()]
    if not quiet:
        print('\n'.join(out))
    return out

def cmd_handoff(args):
    c = cfg(); note = ' '.join(args).strip()
    br, head, behind, nchg = git_state(c)
    _, log, _ = sh('git log -5 --format="%h %cs %s"')
    _, st, _ = sh('git status --short')
    old = read('docs/HANDOFF.md')
    notes = re.findall(r'^- \[\d{4}-\d{2}-\d{2} [^\]]*\] .*$', old, re.M)[-6:]
    if note:
        notes.append('- [%s %s] %s' % (today(), datetime.datetime.now().strftime('%H:%M'), note))
    nx = plan_next()
    t = ['# HANDOFF (written %s by `brain.py handoff`; the next chat reads this first)' % datetime.datetime.now().strftime('%Y-%m-%d %H:%M'), '',
         'Notes (newest last):'] + (notes or ['- (none yet: `brain.py handoff "what is half done, what to verify"`)'])
    if nx:
        t += ['', 'Plan next step:'] + ['- [%s] %s' % x for x in nx]
    t += ['', 'Branch: %s @ %s | behind origin/%s: %s' % (br, head, c['main_branch'], behind if behind is not None else 'unknown'), '',
          'Last commits:'] + ['- ' + x for x in log.split('\n') if x] + ['', 'Uncommitted (%d):' % nchg] + ['- ' + x for x in st.split('\n')[:20] if x]
    write('docs/HANDOFF.md', '\n'.join(t) + '\n')
    print('handoff: docs/HANDOFF.md written (%d tokens)' % est('\n'.join(t)))
    return 0

def cmd_brief(args):
    c = cfg(); out = [TERSE, '']
    g = read('AI_GUIDE.md'); proj = ''
    for t, body in guide_sections():
        if t.lower().startswith('project in'):
            proj = '\n'.join(x for x in body.split('\n')[1:] if x.strip() and not x.strip().endswith(':') and 'FILL' not in x)[:900]
    out.append('PROJECT: ' + (proj.replace('\n', '\n  ') if proj else '(AI_GUIDE.md "Project in 10 lines" is empty: fill it from the code, it is the most valuable section)'))
    out += ['', 'LOOP: plan in docs/ -> rebase on origin/%s -> research (2 sources) -> own file + test -> `check` -> STRUCTURE.md + changelog + `learn` -> commit, rebase, push -> UI: screenshots, wait for OK.' % c['main_branch'],
            'TOOLS: ctx "task" | find | show | outline | impact | tests-for | map DIR | guide SEC | run "cmd" | diff | learn | audit | handoff | secrets',
            'NEVER read AI_GUIDE.md / STRUCTURE.md / symbols.idx / generated or built files whole.', '']
    out += cmd_start(quiet=True)
    for t, body in guide_sections():
        if t.startswith('7'):
            lines = [x for x in body.split('\n') if x.startswith('- ')][-4:]
            if lines:
                out += ['', 'RECENT CHANGES:'] + [x[:170] for x in lines]
    nl = len(parse_lessons())
    if nl:
        out.append('LESSONS: %d recorded (ctx surfaces the relevant ones)' % nl)
    task = ' '.join(args).strip()
    text = '\n'.join(out)
    print(text)
    print('\n[brief ~%d tokens]' % est(text))
    if task:
        print(); cmd_ctx([task])
    return 0

def cmd_cost():
    g, s = read('AI_GUIDE.md'), read('STRUCTURE.md')
    files = project_files()
    rows = sorted(((est(text_of(f)), f) for f in files), reverse=True)[:8]
    import io, contextlib
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        cmd_brief([])
    b = est(buf.getvalue())
    print('reading AI_GUIDE.md whole: ~%d tokens | STRUCTURE.md whole: ~%d | both: ~%d' % (est(g), est(s), est(g) + est(s)))
    print('`brief`: ~%d tokens  -> saves ~%d%% of the cold start' % (b, round(100 - 100 * b / max(1, est(g) + est(s)))))
    print('whole project: %d files, ~%s tokens (never read it; use ctx/show)' % (len(files), format(sum(est(text_of(f)) for f in files), ',')))
    print('most expensive files to read whole:')
    for t, f in rows:
        print('  ~%6d  %s' % (t, f))
    return 0

# ------------------------------------------------------------------ inventory
def cmd_inventory():
    c = cfg()
    files = [f for f in project_files(c) if f != 'docs/ALL_FEATURES.md']
    listed = parse_structure()
    rows = [(f,) + lines_bytes(f) + (listed.get(f, ''),) for f in files]
    tl, tb = sum(r[1] for r in rows), sum(r[2] for r in rows)
    by_area, by_ext = {}, {}
    for r in rows:
        a = by_area.setdefault(area_of(r[0]), [0, 0, 0]); a[0] += 1; a[1] += r[1]; a[2] += r[2]
        e = by_ext.setdefault(os.path.splitext(r[0])[1].lower() or '(none)', [0, 0, 0]); e[0] += 1; e[1] += r[1]; e[2] += r[2]
    W = ['# %s - everything inside (one-file inventory)' % (c['project'] or os.path.basename(ROOT)), '',
         '> Generated %s by `python3 brain/brain.py inventory`. Do not hand-edit. Purposes come from STRUCTURE.md.' % today(), '',
         '- Files: **%d** - Lines: **%s** - Size: **%s**' % (len(rows), format(tl, ','), kb(tb)), '', '## By area', '', '| Area | Files | Lines | Size |', '|---|---:|---:|---:|']
    for a, v in sorted(by_area.items(), key=lambda kv: -kv[1][1]):
        W.append('| `%s` | %d | %s | %s |' % (a, v[0], format(v[1], ','), kb(v[2])))
    W += ['', '## By file type', '', '| Type | Files | Lines | Size |', '|---|---:|---:|---:|']
    for e, v in sorted(by_ext.items(), key=lambda kv: -kv[1][1]):
        W.append('| `%s` | %d | %s | %s |' % (e, v[0], format(v[1], ','), kb(v[2])))
    W += ['', '## Every file', '']
    grp = {}
    for r in rows:
        grp.setdefault(os.path.dirname(r[0]) or '(root)', []).append(r)
    for g, rs in sorted(grp.items()):
        W += ['### `%s/` - %d files, %s lines' % (g, len(rs), format(sum(x[1] for x in rs), ',')), '', '| File | Lines | Size | Purpose |', '|---|---:|---:|---|']
        for r in rs:
            W.append('| `%s` | %s | %s | %s |' % (os.path.basename(r[0]), format(r[1], ','), kb(r[2]), (r[3] or '').replace('|', '\\|')))
        W.append('')
    write('docs/ALL_FEATURES.md', '\n'.join(W))
    print('inventory: docs/ALL_FEATURES.md (%d files, %s lines)' % (len(rows), format(tl, ',')))

# ------------------------------------------------------------------ check / hooks
def changed_files():
    _, a, _ = sh('git diff --name-only HEAD'); _, b, _ = sh('git ls-files --others --exclude-standard')
    return [f for f in (a + '\n' + b).split('\n') if f]

def cmd_check(args):
    c = cfg(); ok = True
    cmd_index(c, quiet=True)
    ok &= cmd_structure([]) == 0
    ok &= cmd_secrets(['--staged']) == 0
    for label, key in (('build', 'build'), ('syntax', 'syntax')):
        if c['commands'].get(key):
            rc, out, err = sh(c['commands'][key]); lines, n = compress(out + '\n' + err, rc, 10)
            print('%s: %s [%s]' % (label, 'ok' if rc == 0 else 'FAILED', c['commands'][key][:60]))
            if rc: print('\n'.join('   ' + x for x in lines)); ok = False
    if '--changed' in args:
        ch = changed_files(); tests = []
        for f in ch:
            if is_test(f):
                tests.append(f)
            elif f in project_files():
                tests += tests_for_file(f)
        tests = sorted(set(tests))
        cmds = [test_cmd_for(c, t) for t in tests if test_cmd_for(c, t)]
        print('changed files: %d -> %d relevant tests' % (len(ch), len(cmds)))
    else:
        cmds = c['commands'].get('tests', [])
    for cmd in cmds:
        rc, out, err = sh(cmd); lines, n = compress(out + '\n' + err, rc, 8)
        print('test: %s [%s]' % ('ok' if rc == 0 else 'FAILED', cmd[:70]))
        if rc: print('\n'.join('   ' + x for x in lines)); ok = False
    ok &= cmd_audit(['--quiet']) == 0
    print('CHECK:', 'ALL GOOD' if ok else 'PROBLEMS ABOVE')
    return 0 if ok else 1

HOOK = '''#!/bin/sh
# AI BRAIN pre-commit: fresh index, no secrets, STRUCTURE.md honest. (Audit warnings are shown but never block.)
cd "$(git rev-parse --show-toplevel)" || exit 0
python3 brain/brain.py index >/dev/null 2>&1 && git add symbols.idx AI_GUIDE.md 2>/dev/null
python3 brain/brain.py secrets --staged || { echo "commit blocked: possible secret (see above)"; exit 1; }
python3 brain/brain.py structure || { echo "commit blocked: update STRUCTURE.md (python3 brain/brain.py structure --draft)"; exit 1; }
python3 brain/brain.py audit --quiet || echo "(audit found broken references; run: python3 brain/brain.py audit)"
exit 0
'''

def cmd_hooks():
    write('.githooks/pre-commit', HOOK)
    os.chmod(os.path.join(ROOT, '.githooks', 'pre-commit'), 0o755)
    rc, _, err = sh('git config core.hooksPath .githooks')
    print('hooks: installed .githooks/pre-commit' + ('' if rc == 0 else ' (git config failed: %s)' % err))

# ------------------------------------------------------------------ init / multi-tool pointers
def pointer_text(project):
    return '''# %s - AI instructions (generated by ai-brain-kit; edit docs/AI_START_HERE.md, not this file)

1. FIRST command of every session:  python3 brain/brain.py brief "<your task in a few words>"
2. Do NOT read AI_GUIDE.md, STRUCTURE.md or symbols.idx whole, and never generated/built files. Navigate with:
   ctx "task" | find NAME | show NAME | outline FILE | impact NAME | tests-for FILE | map DIR | guide SECTION   (all: python3 brain/brain.py <cmd>)
3. Run tests/builds through:  python3 brain/brain.py run "<command>"   (failures only; full log in brain/.cache/last_run.log)
4. Output rules (terse): no narration ("let me check..."), no recap of what you just did, no permission-seeking for obvious steps, code/diff first.
   Final reply = what changed / what could not be verified / what the owner must decide.
5. Finish: python3 brain/brain.py check --changed ; update STRUCTURE.md for added/moved files ; add a changelog line (AI_GUIDE section 7) ;
   python3 brain/brain.py learn "what cost time" --ref file ; python3 brain/brain.py handoff "state" if the work is unfinished.
6. Before you commit: python3 brain/brain.py secrets --staged . Rebase on the base branch before every push. UI changes: screenshots first, wait for approval.
''' % project

TOOLS = {  # tool -> [(path, kind)]  kind: plain | mdc | claude
    'claude':   [('CLAUDE.md', 'claude')],
    'agents':   [('AGENTS.md', 'plain')],      # Codex, Jules, Amp, Zed, Warp, Factory, and many others read AGENTS.md
    'cursor':   [('.cursor/rules/brain.mdc', 'mdc'), ('.cursorrules', 'plain')],
    'copilot':  [('.github/copilot-instructions.md', 'plain')],
    'windsurf': [('.windsurf/rules/brain.md', 'plain'), ('.windsurfrules', 'plain')],
    'gemini':   [('GEMINI.md', 'plain')],
    'cline':    [('.clinerules', 'plain')],
    'aider':    [('CONVENTIONS.md', 'plain')],
}
MARK_A, MARK_B = '<!-- BRAIN:START -->', '<!-- BRAIN:END -->'

def write_pointer(path, kind, body):
    cur = read(path)
    block = '%s\n%s\n%s' % (MARK_A, body.strip(), MARK_B)
    if cur and MARK_A in cur:
        new = re.sub(re.escape(MARK_A) + '.*?' + re.escape(MARK_B), lambda m: block, cur, flags=re.S); action = 'updated'
    elif cur:
        new = cur.rstrip('\n') + '\n\n' + block + '\n'; action = 'appended to existing'
    else:
        head = '---\ndescription: Project brain - how to navigate and work in this codebase\nalwaysApply: true\n---\n' if kind == 'mdc' else ''
        new = head + block + '\n'; action = 'wrote'
    write(path, new)
    print('init: %s %s' % (action, path))

def install_pointers(tools, project):
    names = list(TOOLS) if 'all' in tools else tools
    for t in names:
        if t not in TOOLS:
            print('init: unknown tool "%s" (known: %s, all)' % (t, ', '.join(TOOLS))); continue
        for path, kind in TOOLS[t]:
            write_pointer(path, kind, pointer_text(project))

def detect(files):
    names = {os.path.basename(f) for f in files}
    cmds, tcmd = {"build": "", "syntax": "", "tests": []}, ''
    if 'package.json' in names:
        try:
            sc = json.load(open(os.path.join(ROOT, 'package.json'))).get('scripts', {})
        except Exception:
            sc = {}
        cmds['build'] = 'npm run build' if 'build' in sc else ''
        cmds['syntax'] = 'npm run lint' if 'lint' in sc else ''
        cmds['tests'] = ['npm test'] if 'test' in sc else []
    elif names & {'pyproject.toml', 'setup.py', 'requirements.txt'}:
        cmds['syntax'] = 'python3 -m compileall -q .'
        if any(f.startswith('tests/') or os.path.basename(f).startswith('test_') for f in files):
            cmds['tests'] = ['python3 -m pytest -q']; tcmd = 'python3 -m pytest -q {test}'
    elif 'go.mod' in names:
        cmds['build'] = 'go build ./...'; cmds['tests'] = ['go test ./...']
    elif 'Cargo.toml' in names:
        cmds['build'] = 'cargo build'; cmds['tests'] = ['cargo test']
    elif 'Makefile' in names:
        cmds['build'] = 'make'
    return cmds, tcmd

def cmd_init(args):
    c = cfg(); files = project_files(c)
    tools = ['claude', 'agents']
    if '--tool' in args:
        tools = [t.strip() for t in args[args.index('--tool') + 1].split(',') if t.strip()]
    p = os.path.join(HERE, CFG_NAME)
    if not os.path.exists(p):
        cmds, tcmd = detect(files)
        c['project'] = os.path.basename(ROOT); c['commands'] = cmds; c['test_cmd'] = tcmd
        json.dump(c, open(p, 'w', encoding='utf-8'), indent=2); print('init: wrote brain/brain.json (project=%s)' % c['project'])
    tpl = os.path.join(HERE, 'templates'); proj = c['project'] or os.path.basename(ROOT)
    exts = {}
    for f in files:
        e = os.path.splitext(f)[1].lower(); exts[e] = exts.get(e, 0) + 1
    topt = ', '.join('%s x%d' % (e or 'none', n) for e, n in sorted(exts.items(), key=lambda kv: -kv[1])[:6])
    for name, dst in (('AI_GUIDE.md', 'AI_GUIDE.md'), ('PLAN_TEMPLATE.md', 'docs/PLAN_TEMPLATE.md'), ('AI_START_HERE.md', 'docs/AI_START_HERE.md'), ('LESSONS.md', 'docs/LESSONS.md')):
        s = os.path.join(tpl, name)
        if not os.path.exists(os.path.join(ROOT, dst)) and os.path.exists(s):
            write(dst, open(s, encoding='utf-8').read().replace('{{PROJECT}}', proj).replace('{{TOP_TYPES}}', topt)); print('init: wrote', dst)
        elif os.path.exists(os.path.join(ROOT, dst)):
            print('init: kept existing', dst)
    install_pointers(tools, proj)
    gi = read('.gitignore')
    if 'brain/.cache' not in gi:
        write('.gitignore', (gi.rstrip('\n') + '\n' if gi else '') + 'brain/.cache/\n__pycache__/\n')
    if not read('STRUCTURE.md'):
        write('STRUCTURE.md', '# STRUCTURE.md - every file and what it does\n\nOne line per file: ``- `path` — what it does``. `python3 brain/brain.py structure` fails if a file is missing here.\n'
                             'Rule: add/rename/move/delete a file => update this file in the same change. Do not read it whole: `brain.py map DIR`.\n')
    global _FILES
    _FILES = None
    cmd_structure(['--draft']); cmd_index(c)
    if '--no-hooks' not in args:
        cmd_hooks()
    print('\ninit done. Next (ask an AI with the codebase-brain skill): fill AI_GUIDE.md sections "Project in 10 lines", Architecture, Router; replace every "describe me" in STRUCTURE.md.\nThen: python3 brain/brain.py brief')

def main():
    a = sys.argv[1:]
    cmd = a[0] if a else 'help'; r = a[1:]
    table = {'init': lambda: cmd_init(r), 'index': lambda: cmd_index(), 'find': lambda: cmd_find(r), 'outline': lambda: cmd_outline(r), 'show': lambda: cmd_show(r),
             'structure': lambda: cmd_structure(r), 'map': lambda: cmd_map(r), 'guide': lambda: cmd_guide(r), 'ctx': lambda: cmd_ctx(r), 'impact': lambda: cmd_impact(r),
             'tests-for': lambda: cmd_tests_for(r), 'run': lambda: cmd_run(r), 'diff': lambda: cmd_diff(r), 'learn': lambda: cmd_learn(r), 'audit': lambda: cmd_audit(r),
             'secrets': lambda: cmd_secrets(r), 'handoff': lambda: cmd_handoff(r), 'brief': lambda: cmd_brief(r), 'cost': lambda: cmd_cost(), 'inventory': lambda: cmd_inventory(),
             'start': lambda: (cmd_start(), 0)[1], 'check': lambda: cmd_check(r), 'hooks': lambda: cmd_hooks(), 'version': lambda: print('ai-brain', VERSION)}
    if cmd == 'selftest':
        import runpy
        return runpy.run_path(os.path.join(HERE, 'selftest.py'), run_name='__main__').get('RESULT', 0)
    if cmd in table:
        return table[cmd]()
    print(__doc__); return 0

if __name__ == '__main__':
    sys.exit(main() or 0)
