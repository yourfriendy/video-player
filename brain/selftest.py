#!/usr/bin/env python3
"""python3 brain/brain.py selftest  - builds a throw-away project in a temp folder and checks every command. Exit 0 = the kit works on this machine."""
import os, shutil, subprocess, sys, tempfile
HERE = os.path.dirname(os.path.abspath(__file__))
RESULT = 1
def run(cmd, cwd, ok=(0,)):
    r = subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr)
def main():
    global RESULT
    tmp = tempfile.mkdtemp(prefix='brain_selftest_'); fails = []
    def check(name, cond, info=''):
        print(('ok    ' if cond else 'FAIL  ') + name + ('' if cond else '  ' + info[:200]))
        if not cond: fails.append(name)
    try:
        for d in ('src/auth', 'tests'): os.makedirs(os.path.join(tmp, d))
        open(os.path.join(tmp, 'src/auth/session.ts'), 'w').write('/** Session handling */\nexport function createSession(u: string) {\n  return { id: u };\n}\nexport const MAX_AGE = 3600;\n')
        open(os.path.join(tmp, 'src/app.py'), 'w').write('"""Main app"""\nclass App:\n    def run(self):\n        return 1\n')
        open(os.path.join(tmp, 'tests/session.test.ts'), 'w').write('import { createSession } from "../src/auth/session";\ncreateSession("a");\n')
        open(os.path.join(tmp, 'tests/fail.py'), 'w').write('for i in range(200): print("ok", i)\nraise RuntimeError("boom")\n')
        run('git init -q && git config user.email t@t && git config user.name t && git add -A && git commit -qm init', tmp)
        shutil.copytree(HERE, os.path.join(tmp, 'brain'), ignore=shutil.ignore_patterns('__pycache__', '.cache', 'brain.json'))
        B = 'python3 brain/brain.py '
        rc, o = run(B + 'init --tool all', tmp); check('init', rc == 0 and os.path.exists(os.path.join(tmp, 'AI_GUIDE.md')), o)
        for f in ('AGENTS.md', 'CLAUDE.md', 'GEMINI.md', '.cursor/rules/brain.mdc', '.github/copilot-instructions.md', '.windsurf/rules/brain.md', '.clinerules', 'CONVENTIONS.md'):
            check('pointer ' + f, os.path.exists(os.path.join(tmp, f)))
        rc, o = run(B + 'init --tool all', tmp); check('init is idempotent', rc == 0 and open(os.path.join(tmp, 'AGENTS.md')).read().count('BRAIN:START') == 1, o)
        rc, o = run(B + 'find createSession', tmp); check('find', 'src/auth/session.ts:2' in o, o)
        rc, o = run(B + 'outline src/auth/session.ts', tmp); check('outline', 'createSession' in o and 'MAX_AGE' in o, o)
        rc, o = run(B + 'show createSession', tmp); check('show', 'return { id: u }' in o, o)
        rc, o = run(B + 'ctx "session login"', tmp); check('ctx', 'src/auth/session.ts' in o, o)
        rc, o = run(B + 'impact createSession', tmp); check('impact', 'tests/session.test.ts' in o and 'blast radius' in o, o)
        rc, o = run(B + 'tests-for src/auth/session.ts', tmp); check('tests-for', 'session.test.ts' in o, o)
        rc, o = run(B + 'run "python3 tests/fail.py"', tmp); check('run compresses', rc == 1 and 'RuntimeError' in o and o.count('\n') < 14, o)
        rc, o = run(B + 'learn "Never cache sessions" --ref src/auth/session.ts,createSession', tmp); check('learn', rc == 0, o)
        rc, o = run(B + 'learn "old" --ref src/gone.ts', tmp); rc, o = run(B + 'audit', tmp); check('audit finds a broken ref', rc == 1 and 'gone.ts' in o, o)
        rc, o = run(B + 'handoff "half done"', tmp); check('handoff', os.path.exists(os.path.join(tmp, 'docs/HANDOFF.md')), o)
        rc, o = run(B + 'brief "session"', tmp); check('brief', 'OUTPUT RULES' in o and 'half done' in o and 'ctx:' in o, o)
        rc, o = run(B + 'map src', tmp); check('map', 'src/app.py' in o, o)
        rc, o = run(B + 'guide', tmp); check('guide lists sections', 'Token-saver' in o, o)
        rc, o = run(B + 'cost', tmp); check('cost', 'brief' in o, o)
        rc, o = run(B + 'inventory', tmp); check('inventory', os.path.exists(os.path.join(tmp, 'docs/ALL_FEATURES.md')), o)
        open(os.path.join(tmp, 'leak.py'), 'w').write('K = "ghp_' + 'a1' * 20 + '"\n'); run('git add leak.py', tmp)
        rc, o = run(B + 'secrets --staged', tmp); check('secrets detects a token', rc == 1 and 'GitHub token' in o, o)
        rc, o = run('git commit -qm leak', tmp); check('pre-commit hook blocks a secret', rc != 0, o)
        run('git rm -qf --cached leak.py && rm leak.py', tmp)
        open(os.path.join(tmp, 'newfile.py'), 'w').write('"""New file"""\n'); run('git add newfile.py', tmp)
        rc, o = run(B + 'structure', tmp); check('structure notices a new file', rc == 1 and 'newfile.py' in o, o)
        rc, o = run(B + 'structure --draft', tmp); rc, o = run(B + 'structure', tmp); check('structure --draft fixes it', rc == 0, o)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print('\nselftest: %s' % ('ALL PASSED' if not fails else '%d FAILED: %s' % (len(fails), ', '.join(fails))))
    RESULT = 0 if not fails else 1
    return RESULT
if __name__ == '__main__':
    sys.exit(main())
