---
name: codebase-brain
description: Use when starting, continuing or finishing work in ANY codebase - orient in ~600 tokens with `python3 brain/brain.py brief "<task>"`, navigate with ctx/find/show/outline/impact instead of reading files, run tests through `run`, keep lessons/handoff/STRUCTURE.md true, and set the brain up in a new repo (`brain/brain.py init`). Also for "map", "document" or "onboard" a codebase.
---

# Codebase Brain (v2)

The brain = `AI_GUIDE.md` (rules, router, changelog) + `STRUCTURE.md` (every file, one line) + `symbols.idx` (symbol -> file:line) + `docs/LESSONS.md` + `docs/HANDOFF.md` + `brain/brain.py` (the tool). Goal: **least tokens, most accuracy**. Reading AI_GUIDE+STRUCTURE whole costs 10-50k tokens; `brief` costs ~600-900.

## 0. Output rules (always, no exceptions)
No narration ("let me check..."). No recap of what you just did. No asking permission for obvious steps. Code/diff first. Final reply = **what changed / what could not be verified / what the owner must decide**. Park owner decisions in the plan instead of guessing.

## 1. Detect
`brain/brain.py` exists -> **use mode** (section 2). Missing -> **set-up mode** (section 3); do not start feature work first unless told to.

## 2. Use mode
1. `python3 brain/brain.py brief "<task in a few words>"` -> rules, project, git status (**if BEHIND: `git fetch origin && git rebase origin/<main>` first**), plan step, last handoff, and the files / symbols / tests / lessons for the task. This replaces reading AI_GUIDE.md and STRUCTURE.md. Never read those, `symbols.idx`, or generated/built files whole.
2. Need more, in this order of cost: `guide <section>` · `map <DIR>` · `ctx "<words>"` (find by meaning) · `find NAME` · `outline FILE` (symbols + line numbers) · `show NAME` (one body) · then `Read` with offset/limit.
3. **Before editing a symbol: `impact NAME`** (callers, importers, tests, blast radius HIGH/MEDIUM/LOW). HIGH = say so and keep the change minimal/backward compatible.
4. Big work: copy `docs/PLAN_TEMPLATE.md` to `docs/<NAME>_PLAN.md`; work the first unchecked step; tick + Notes + log. Research facts in 2+ sources; never invent tables/formulas/API behaviour; record links.
5. Edit small and exact, in the feature's own file. A test per feature. UI: screenshot at phone width and LOOK at it.
6. **Run builds/tests with `python3 brain/brain.py run "<cmd>"`**: failures + summary only; the full log is in `brain/.cache/last_run.log` (grep it). Never paste long passing logs. `check --changed` runs only the tests that cover your changes.
7. Finish: `diff` (lists new files not yet in STRUCTURE.md) -> update `STRUCTURE.md` -> one changelog line (AI_GUIDE section 7) -> `learn "what cost time" --ref file` -> `check --changed`.
8. Unfinished or context getting long? `handoff "half done: X; verify Y"` - the next chat starts from it automatically.
9. Commit with attribution, `secrets --staged` (the hook does it), rebase on the base branch, push, verify the remote. Visible UI change: commit locally, wait for approval. Never force-push shared branches.

## 3. Set-up mode (new repo, ~5 minutes)
1. Copy the kit's `brain/` folder to the repo root (or `python3 install.py <repo> [--tool all]` from the kit).
2. `python3 brain/brain.py init [--tool all|claude,cursor,copilot,windsurf,gemini,agents,cline,aider]` -> config, AI_GUIDE.md, docs/ templates, STRUCTURE.md draft, symbols.idx, tool pointer files (CLAUDE.md, AGENTS.md, .cursor/rules, copilot-instructions...), pre-commit hook.
3. Fill what only a careful read can write: AI_GUIDE.md "Project in 10 lines", Architecture, **Router** (one row per feature: the user's words -> files -> 2-4 symbols), Gotchas; every "(describe me)" in STRUCTURE.md (what the file DOES, one line).
4. Put real build/syntax/test commands in `brain/brain.json` -> `commands` (and `test_cmd` with `{test}`).
5. `python3 brain/brain.py check` = ALL GOOD, `python3 brain/brain.py audit` has no ERROR. Commit.

## 4. Quality bar
- Router rows use the user's words ("PDF export fails"), not jargon. STRUCTURE lines say what a file DOES. A lesson = what went wrong + the fix + `--ref` to the file/symbol (so `audit` can tell when it goes stale).
- Never put secrets, tokens or personal data in any brain file.

More: `references/working-method.md`, `references/prompts.md`.
