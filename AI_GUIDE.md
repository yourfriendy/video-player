# AI_GUIDE.md - read this FIRST (written for AI assistants and new people)

You are working on **repo**. File types here: .kt x115, .xml x16, .md x11, .kts x4, .yml x3, none x3. This file tells you where things are and how work is done here, so you do not have to explore. **Every token and every minute counts.**

## Project in 10 lines  (FILL THIS IN - the single most valuable section)
- What it is, who uses it, who owns it:
- Main folders and what lives in each:
- How to run it / build it / test it (exact commands):
- How it is deployed (and what must never be pushed):
- The 3 things that surprise newcomers:

## 0a. WORKING METHOD - do exactly like this (the loop every AI and person follows)
1. **Plan first, in a file.** Big work gets a checklist plan in `docs/` (copy `docs/PLAN_TEMPLATE.md`). A new chat resumes from the plan: the first unchecked box is the next step. Tick it when done; add a Notes line and a log line.
2. **Be on the latest base:** `git fetch origin && git rebase origin/<main>` at the start of every task, before every commit and before every push. Never push from a stale base. Never force-push shared branches. If a push is rejected, stop and tell the owner.
3. **Research, then build.** Facts, formulas, tables, API behaviour: verify in at least two independent sources and record the links in the plan Notes. Do not invent data from memory.
4. **Definition of done for each step:** the code in its own file; a test that would fail if the feature broke; the build/syntax command passes; for UI, a screenshot at phone width that you actually LOOKED at; `STRUCTURE.md` updated; changelog line added; `python3 brain/brain.py check` passes.
5. **Wire every new feature in ALL the places it must appear** (menu/registry/router/docs/help text). List those places in section 4 so nobody forgets one.
6. **Push live after each finished backend step** (commit with attribution, rebase, push, verify with `git ls-remote`), then start the next step.
7. **Visible UI changes: show first, push after approval.** Build it, test it, send screenshots, commit locally, WAIT for the owner to say push.
8. **Decisions that belong to the owner** (tone, which events, which school/rule, pricing, wording) go in the plan under "Owner decisions pending". Do the work that does not depend on them, park the rest, say so plainly.
9. **Keep replies short:** what changed, what could not be verified, what the owner must decide. Say when you start and when a step is live.

## 0. Token-saver rules (the brain enforces most of them)
1. **Start with `python3 brain/brain.py brief "<task>"`** (~600 tokens) instead of reading this file and STRUCTURE.md whole (usually 10-50k tokens). Read one section only: `guide <section>`; one folder only: `map <DIR>`.
2. **Find by meaning:** `ctx "<task>"` -> best files, symbols, tests, lessons. **Find by name:** `find NAME`. **See a file cheaply:** `outline FILE`. **Read one symbol:** `show NAME`. Read ranges, never whole files.
3. **Before changing a symbol:** `impact NAME` (callers, importers, tests, blast radius). **After:** `tests-for FILE`, then `check --changed`.
4. **Run builds/tests through `run "<cmd>"`**: you get failures + summary only (full log is saved, grep it). Never paste a 400-line passing log into the chat.
5. Never read generated or built files (bundles, `dist/`, `build/`, minified, lockfiles).
6. Edit with exact small replacements. Do not rewrite whole files or re-paste big blocks. One build at the end, not after every edit.
7. **Terse output:** no narration ("let me check..."), no recap of what you just did, no asking permission for obvious steps, code/diff first. Final reply = what changed / what could not be verified / what the owner must decide.
8. Do not spawn sub-agents or search the web for things this file already answers. Ambiguous and cheap to redo: pick the most reasonable reading and say which; ask only for expensive or irreversible choices.
9. Whenever you add/rename/move/delete a file or add a feature, update `STRUCTURE.md` in the same change (`diff` lists new files not yet described; the pre-commit hook enforces it).
10. Something cost you time? `learn "..." --ref file`. Work unfinished? `handoff "state"`. Done? one changelog line in section 7 + `check`.

## 1. Architecture in 10 lines  (FILL IN)
-

## 2. Router - "if the request is about X, go to Y"  (FILL IN, one row per feature/area)
| The request is about | Start in (files) | Symbols to search |
|---|---|---|
| example: login / sessions | `src/auth/session.ts` | `createSession`, `refreshToken` |

## 3. Commands
| What | Command |
|---|---|
| Session start (rules + project + status + handoff [+ task context]) | `python3 brain/brain.py brief "task"` |
| Context for a task / find a symbol / outline / read a symbol | `ctx "task"` / `find NAME` / `outline FILE` / `show NAME` |
| Who uses it? Which tests? | `impact NAME` / `tests-for FILE` |
| Run something, see only failures | `run "<cmd>"` |
| What did I change? | `diff` (`--hunks`) |
| Everything (index, structure, secrets, build, syntax, tests; `--changed` = only relevant tests) | `check [--changed]` |
| Keep memory true | `learn "..." --ref x` / `audit` / `handoff "note"` / `structure --draft` |
| One-file inventory of the whole project | `inventory` -> `docs/ALL_FEATURES.md` |
(all start with `python3 brain/brain.py`)
| Build / test the project | (fill in; also put them in `brain/brain.json` "commands") |

## 4. Rules and gotchas (learned the hard way - add one line each time something cost you time)
- **RELEASE RULE: every update and every APK goes to a NEW GitHub Release, never only to a workflow artifact and never by overwriting an older release. Every push to main (or manual run on main) -> a new release `v<version>.<build>` (e.g. v1.1.114) marked Latest, with OffScroll.apk, OffScroll-Engine.apk and SHA256SUMS.txt; a pushed `v*` tag -> a release for that tag; any other branch -> pre-release `build-<branch>`. Keep the `Build number: <n>` line in the notes (UpdateModels.kt reads it). build-apk.yml fails the run if the APK is missing from its release. If a build is not in Releases, fixing that comes first. Never cancel a main build; keep the one retry. Notification-listener access (BIND_NOTIFICATION_LISTENER_SERVICE) was removed on purpose (Play Protect): do not add it back.
- Generated files (never hand-edit): 
- Places a new feature must be registered:
- Naming / numbering / ordering conventions:
- Things that look wrong but are intentional:

## 5. Deliver
- How a change reaches users (branch, CI, deploy), what to verify after it is live, who approves what.

## 6. Keeping this file alive
- `python3 brain/brain.py index` rewrites the AUTO block below and `symbols.idx`. The git pre-commit hook (`python3 brain/brain.py hooks`, once per clone) does it on every commit and blocks a commit when STRUCTURE.md is stale.
- The hand-written parts change only when behaviour or workflow changes. After every task add one changelog line.

## 7. State and changelog (newest last; keep it to ~25 lines, merge old ones)
- repo brain installed.

## 8. Auto-generated map (do not edit)
<!-- AUTO:START (rewritten by `python3 brain/brain.py index`; keep this block small) -->
_2026-11-27: 196 files, 35,459 lines, 888 symbols. Biggest areas:_

| Area | Files | Lines |
|---|---:|---:|
| `video player` | 165 | 31,626 |
| `brain` | 7 | 1,502 |
| `(root)` | 7 | 1,440 |
| `.github` | 6 | 520 |
| `docs` | 6 | 261 |
| `.claude` | 4 | 102 |
| `.githooks` | 1 | 8 |
<!-- AUTO:END -->
