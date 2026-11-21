# The working method (and why each step exists)

| # | Step | Why |
|---|---|---|
| 1 | Plan in a file with a checklist | A new chat or a new person resumes from the first unchecked box; nothing lives only in someone's head or a chat. |
| 2 | Rebase on the latest base at start, before commit, before push | Parallel work is normal. A stale base creates conflicts and overwrites other people's work. |
| 3 | Two-source research, links in the plan | Tables, formulas and API details from memory are the most common source of silent bugs. |
| 4 | Definition of done: own file, test, build, screenshot you looked at, STRUCTURE.md, changelog, `brain.py check` | "Works on my machine" is not done. The checklist makes done mean the same every time. |
| 5 | Wire a new feature in every place it must appear | Features are lost when one registry/menu/help entry is forgotten. List the places in AI_GUIDE section 4. |
| 6 | Push live after each finished backend step | Small, verified, reversible steps; the owner sees progress. |
| 7 | UI: show first, push after approval | Looks are a product decision; the owner decides. |
| 8 | Park owner decisions | Wording, pricing, rules are not engineering calls. Do all the work that does not depend on them. |
| 9 | Short replies | Changed / could not verify / owner must decide. Nothing else. |

## Token rules in one breath
`brief` instead of reading the guide. `ctx`/`find`/`outline`/`show` instead of reading files. `impact` before editing. `run` instead of raw logs. `check --changed` instead of the whole suite. Terse replies. Never read built files.

## Where the savings come from (measured on a 588-file, 2.1M-token project)
| Moment | Naive | With the brain |
|---|---|---|
| Cold start (read AI_GUIDE + STRUCTURE) | ~45,900 tokens | `brief` ~830 tokens (-98%) |
| "Where is X?" | open/grep many files (thousands) | `ctx "X"` 300-1,500 tokens |
| Read a function | read the file (often 2-20k) | `show NAME` ~100-600 tokens |
| Test run with 300 passing lines | ~3k tokens in the chat | `run` ~60 tokens, full log on disk |
| New chat after a dead one | re-explain, re-explore | `handoff` + `brief`: seconds |

## Keeping the brain true
- `brain.py index` on every commit (the hook does it): symbols.idx + file map never go stale.
- `brain.py structure` blocks a commit when a file is not described in STRUCTURE.md.
- Changelog: one line per task in AI_GUIDE section 7; merge old lines when it passes ~25.
- Gotchas: add one line each time something cost time.
