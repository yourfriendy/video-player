# AI start here ({{PROJECT}})

**Start prompt the owner pastes in a new chat:**
> Run `python3 brain/brain.py brief "<task>"` and follow docs/AI_START_HERE.md. Task: <continue the plan X / describe the task>. Keep replies short.

## Session loop (the same every time)
1. `python3 brain/brain.py start` - branch, behind the base?, next unchecked plan item. If behind: rebase first.
2. Read `AI_GUIDE.md` (rules + router) once, `STRUCTURE.md` once. Find code with `python3 brain/brain.py find NAME`; read only that range.
3. Work the next step of the plan. Small edits, own files, a test per feature.
4. `python3 brain/brain.py check --changed` (index, structure, secrets, build, syntax, only the tests that cover your change). Run any command through `run "<cmd>"`.
5. Update `STRUCTURE.md` (files), the plan (tick + Notes + log), `AI_GUIDE.md` section 7 (one line), `learn "..."` for anything that cost time. Unfinished? `handoff "state"`.
6. Commit with the attribution lines, rebase, push, verify the remote. UI changes: screenshots first, wait for approval.
7. Reply: what changed / what could not be verified / what the owner must decide.

## Lessons learned (add a line every time something cost time)
-
