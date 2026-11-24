# AI start here (repo)

**Start prompt the owner pastes in a new chat:**
> Run `python3 brain/brain.py brief "<task>"` and follow docs/AI_START_HERE.md. Task: <continue the plan X / describe the task>. Keep replies short.

## Session loop (the same every time)
1. `python3 brain/brain.py start` - branch, behind the base?, next unchecked plan item. If behind: rebase first.
2. Read `AI_GUIDE.md` (rules + router) once, `STRUCTURE.md` once. Find code with `python3 brain/brain.py find NAME`; read only that range.
3. Work the next step of the plan. Small edits, own files, a test per feature.
4. `python3 brain/brain.py check --changed` (index, structure, secrets, build, syntax, only the tests that cover your change). Run any command through `run "<cmd>"`.
5. Update `STRUCTURE.md` (files), the plan (tick + Notes + log), `AI_GUIDE.md` section 7 (one line), `learn "..."` for anything that cost time. Unfinished? `handoff "state"`.
6. Commit with the attribution lines, rebase, push, verify the remote. UI changes: screenshots first, wait for approval. **After the push, confirm the build became a new GitHub Release** (see the release rule below).
7. Reply: what changed / what could not be verified / what the owner must decide.

## Release rule (never skip)
**Every update and every APK goes to a NEW GitHub Release, never only to a workflow artifact, and never by overwriting an older release.**
- Every push to `main` (or manual run on main) -> a new release `v<version>.<build>` (for example `v1.1.114`), marked **Latest**, with `OffScroll.apk`, `OffScroll-Engine.apk` and `SHA256SUMS.txt`. Older releases stay exactly as they were. "Latest" is just the newest one (what the README button, `/releases/latest` and the in-app updater follow).
- A pushed `v*` tag -> a release for that tag, marked Latest.
- Any other branch -> pre-release `build-<branch>` (replaced by that branch's next build).
- The release notes always keep the line `- Build number: <n>` (UpdateModels.kt reads it).
- The workflow fails the run if the APK is not in the release it should be in. If a build is missing from Releases, fixing that comes before any other work.

## Lessons learned (add a line every time something cost time)
-
