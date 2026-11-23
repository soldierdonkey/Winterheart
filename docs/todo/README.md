# Todo workflow

How multi-step work is tracked. The list of open todos is [../TODO.md](../TODO.md). Each todo has one file in this folder holding its plan, steps and notes.

## Files

- `docs/TODO.md`: index of open todos, one row each.
- `docs/todo/README.md`: this workflow.
- `docs/todo/_template.md`: template for a todo file.
- `docs/todo/<feature-name>-<slug>.md`: one file per todo.

## Naming

`<feature-name>-<slug>.md`, kebab-case. `<feature-name>` is the name from the [feature index](../README.md#feature-index). Use `general` if the todo spans several features. Example: `guides-lost-page-drops.md`.

## Adding a todo

1. Copy [_template.md](_template.md) to `docs/todo/<feature-name>-<slug>.md` and fill it in. Split the work into checkbox steps.
2. Add a row to [../TODO.md](../TODO.md) using the row template there. `Added` is today's date (`YYYY-MM-DD`). `Progress` is `0/N`, where N is the number of steps.

## Working on a todo

- Tick steps (`- [x]`) in the todo file as they land.
- Update `Progress` in `TODO.md` in the same commit. It is done steps / total steps, e.g. `3/7`. It is a rough measure, not an estimate of time.
- Steps can be added, split or removed as the plan changes. Update the total when they are.
- Notes, decisions and dead ends go in the todo file's **Notes** section, not in `TODO.md`.
- Commits follow [AGENTS.md](../../AGENTS.md) as usual (feature-name prefix, docs in the same commit).

## Finishing a todo

1. Record the outcome in the feature doc's **Changes** section (and CHANGELOG.md if player-visible).
2. Remove the row from `TODO.md` and delete the todo file in the same commit. Git history keeps it.

Abandoned todos are removed the same way. Put the reason in the commit message.
