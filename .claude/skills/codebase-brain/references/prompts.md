# Prompts to paste

**Any new chat on a repo that has the brain**
> Run `python3 brain/brain.py brief "<task>"` and follow it. Terse output. Continue the plan step / do: <task>.

**Set the brain up in a repo that has none**
> Use the codebase-brain skill. Run init, then read the code and fill AI_GUIDE.md (Project in 10 lines, Architecture, Router, Gotchas) and every "describe me" line in STRUCTURE.md. Do not change source code. Finish with `check` and `audit`.

**Map an unfamiliar codebase**
> Use the codebase-brain skill. Run `inventory` and `cost`, then write a one-page explanation: what this project is, main parts, where to start reading, what is risky to change (use `impact` on the 5 most used symbols).

**Before changing something risky**
> Run `impact <symbol>` and `tests-for <file>`; tell me the blast radius and the plan before editing.

**Finish a task**
> `diff`, update STRUCTURE.md, add the changelog line, `learn` anything that cost time, `check --changed`, commit with attribution, rebase, push, verify the remote.

**Context is getting long / ending the session**
> Run `python3 brain/brain.py handoff "<what is half done, what to verify next>"`.

**Health check**
> Run `python3 brain/brain.py audit` and fix every ERROR; list the WARNs by importance.
