# agent-commands

Project slash commands for Claude Code, stored in `.claude/commands/`. Each file is a prompt that the agent follows when a contributor types `/<name>`.

## Files

- `.claude/commands/resolve.md`: `/resolve <plan file>`, which resolves the open questions in a plan or draft.
- `.claude/settings.json`: project permissions (allows `git add` and `git commit`, so rule 8 commits run without prompts).
- `AGENTS.md`: contributor rules. `CLAUDE.md` imports it so Claude Code always loads it.

## How it works

### /resolve

The recommended way to fill out a skeleton plan, such as a new `docs/todo/<feature-name>-<slug>.md`. Write the outline with its open questions marked, then run `/resolve <path>`. The full procedure is in the command file. In short:

1. **Collect:** it gathers open questions from "Open questions" sections, inline markers (`TBD`, `TODO`, `?`, `not decided`) and ambiguities the agent spots itself (labelled "inferred"). It ranks them most consequential first and shows the list.
2. **Ask:** it takes the questions from the top of the list.
   - **Detailed** questions (control flow, architecture, major UI changes, anything other questions depend on) are asked one at a time in chat. Each option gets pros, cons and an example, plus a recommended option with reasoning. The user replies in text or says *defer*.
   - **Quick** questions (names, syntax, config keys) go in the question dialog, up to 4 independent ones at a time, with the recommended option first.
3. **Update:** answers can add follow-up questions, which are inserted by rank. Questions that earlier answers settle are auto-resolved, with a one-line note the user can overrule.
4. **Write:** after each round, it adds each answer as a row in the plan's `## Decisions` table, rewrites the steps it affects, and adds a dated `## Notes` line for each detailed answer. This follows the format used in `docs/todo/`.
5. **Commit:** it commits only the plan file, prefixed with the plan's feature name (`<feature-name>: resolve open questions in <slug>`). It does not carry out the plan.

## Config / tunables

None.

## Changes

- 2026-11-22: Added `/resolve`, `AGENTS.md`, `CLAUDE.md` (imports AGENTS.md so Claude Code loads it) and `.claude/settings.json` (allows `git add`/`git commit`), ported from the Soulrend development instance.

## Gotchas

- In detailed mode the agent waits for a typed reply instead of opening the question dialog, so that the pros/cons text stays visible.
- If the plan is new, `/resolve` doesn't add its `docs/TODO.md` row. It only says the row is missing.
