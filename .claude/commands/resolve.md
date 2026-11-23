---
description: Resolve the open questions in a plan or draft, one at a time, then commit the plan
argument-hint: <path to plan file>
---

# /resolve

Help the user settle every open question in a plan file quickly. Go from the most consequential question to the least, write each answer into the plan, then commit the plan. **Do not carry out the plan.** Stop after the commit.

Plan file: `$ARGUMENTS`

If `$ARGUMENTS` is empty or the file does not exist, ask the user which plan to use. Offer the files in `docs/todo/` as options in the question dialog. Then continue.

## Definitions

- **Open question**: an undecided point in the plan. Find them in three ways:
  1. Items under an "Open questions", "Questions", "Undecided" or similar heading.
  2. Inline markers: `TBD`, `TODO`, `?`, `not decided`, `either … or …`, `maybe`, `(decide later)`.
  3. **Inferred**: ambiguous or contradictory parts you notice yourself, e.g. a step that can be built two materially different ways, or two sections that disagree. Label these "inferred" when you list them.
- **Stack**: your working list of unresolved questions, sorted most consequential first. Keep it in your own context. You don't need to write it to a file.
- **Detailed question**: a question whose answer changes a lot or constrains later choices. Examples: control flow or architecture, data formats or storage, a major UI restructure, scope, anything other questions depend on.
- **Quick question**: a small, local choice a programmer would not deliberate over. Examples: names, syntax, config keys and defaults, file locations, wording, ordering.
- **Auto-resolved question**: a question whose answer follows from an answer the user already gave, so the user doesn't need to be asked.

## Steps

### 1. Collect and rank

1. Read the whole plan file. Also read any files it relies on if you need them to judge the questions (code, docs, linked plans).
2. Collect every open question, using all three methods above.
3. Rank them by consequentiality: how much of the plan an answer changes, how many other questions depend on it, and how expensive it would be to reverse later. A question that others depend on always ranks above them.

### 2. Split into two groups

Tag each question **detailed** or **quick** using the definitions above. If you're unsure, tag it detailed. Because of the ranking, detailed questions will mostly sit at the top of the stack.

Before asking anything, show the user the ordered stack as a short numbered list: one line per question, with its tag (`detailed` / `quick`) and `inferred` where it applies. Then start step 3 straight away. Don't wait for approval of the list. The user can interrupt if the list is wrong.

### 3. Ask the top of the stack

Take the top question from the stack.

**Detailed mode** (a detailed question at the top of the stack): ask in **chat text** and wait for a typed reply. Don't use the question dialog. Write:

- **Question**: one or two sentences saying what is undecided and why it matters (what it affects later on).
- **Options** (2–4): for each option, give:
  - a name and a one-line summary,
  - **Pros** (bullets),
  - **Cons** (bullets),
  - **Example**: a short concrete illustration where you can give one (a code snippet, config excerpt, layout sketch, or a walk-through of the behaviour).
- **Recommendation**: name exactly one option as recommended, with your reasoning (why its trade-offs fit this project better than the others).
- End with: "Reply with an option, a variation, or your own answer. You can also say *defer* to leave it open."

Ask only one detailed question per message.

**Quick mode** (a quick question at the top of the stack): use the `AskUserQuestion` dialog.

- Batch up to 4 quick questions in one dialog. Take them from the top of the stack in order. Only batch questions that are **independent**: no answer may change another's options. Stop the batch at the first question that depends on one already in it, or at the first detailed question.
- Each question gets 2–4 options. Each option has a short label and a description with a brief example and a one-line reason. No pros/cons breakdown.
- Mark exactly one option per question as recommended: put it first and add ` (Recommended)` to its label.
- Each question must make sense on its own, because chat text may be hidden while the dialog is open. Name the plan item it concerns.

### 4. Apply the answer and update the stack

1. Record the user's answer. If they chose "Other" or replied freely, use their own wording. If they said *defer*, leave the question open in the plan, mark it `(deferred YYYY-MM-DD)`, and move on.
2. If the answer raises **new** open questions (follow-ups, sub-choices), tag each one detailed or quick and insert it into the stack at its rank. Don't just append it to the end.

### 5. Auto-resolve

Check every remaining question in the stack. If an answer given so far settles a question, resolve it without asking, using the answer that follows from it. Likewise, if an answer makes a question irrelevant, drop it. After each round, tell the user in one line per question what was auto-resolved or dropped and why, e.g. `Auto-resolved: "cache location" → tools/guide_editor/cache/ (follows from "editor lives in tools/").` The user can overrule it, and if they do, treat the question as reopened.

### 6. Write to the plan

Update the plan file after every round (one detailed answer, or one quick batch, plus any auto-resolutions). Don't wait until the end.

- Add each resolved question as a row in a `## Decisions` table (`| Topic | Decision |`). If there is no such section, create it near the top, after the summary, with the line `Settled on YYYY-MM-DD. Change them here if the plan changes.` Keep decisions short and concrete, with exact IDs and paths.
- Remove the question from wherever it was open (open-questions list, inline marker). Rewrite any affected steps, scope or file lists so the plan reads as decided.
- For detailed questions, add a dated line to the plan's `## Notes` section (create it if missing). Give the options that were considered and the reason for the choice, in one or two sentences. Quick and auto-resolved decisions need no note.
- Follow the plan's existing structure and style. Don't reformat parts you didn't change.

### 7. Repeat

Go back to step 3 with the new top of the stack. Continue until the stack is empty (deferred questions don't count).

### 8. Commit

Once every open question is resolved or deferred:

1. Re-read the plan once to check that it is consistent: no leftover markers for resolved questions, and the steps match the decisions.
2. Commit **only the plan file**: `git add <plan file>`, never `git add -A`.
   - Prefix the message with the feature name, following AGENTS.md. Take it from the plan's `Feature:` line, or from its filename (`docs/todo/<feature-name>-<slug>.md`). If neither gives it, use `general`.
   - First line: `<feature-name>: resolve open questions in <plan slug>`.
   - Body: one bullet per decision, in the form `topic: decision`. List deferred questions too, if any. Add the line `Plan only; nothing executed.`
3. If the plan is new and the repo workflow expects other files with it (e.g. a row in `docs/TODO.md`), say so in your reply. Don't add those files yourself.
4. Give the user a short summary: how many questions were resolved (asked / auto-resolved), which were deferred, and the commit hash.

**Do not start carrying out the plan.** Wait until the user asks.

## Rules

- Never decide a detailed question for the user. Only auto-resolve when the answer follows directly from their earlier answers.
- Always mark exactly one option as recommended, in both modes.
- If the user changes an earlier decision partway through, update its row in the Decisions table, then reopen any questions that were auto-resolved from it and re-rank them.
- If the user says to stop, write everything decided so far to the plan, leave the rest open, and ask whether to commit.
