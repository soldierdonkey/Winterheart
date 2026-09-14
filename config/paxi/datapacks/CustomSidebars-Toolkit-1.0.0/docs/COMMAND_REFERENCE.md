# Command Reference

This toolkit is built around the `/cssidebar` command provided by the Custom Sidebars mod.

## Create a Sidebar

```mcfunction
/cssidebar add <id> <title>
/cssidebar set <id> theme <theme>
/cssidebar set <id> layer <event|mission|server|debug>
/cssidebar set <id> priority <number>
/cssidebar set <id> exclusive <true|false>
/cssidebar set <id> global <true|false>
```

## Position and Layout

```mcfunction
/cssidebar set <id> anchor <top_right|middle_right|bottom_right>
/cssidebar set <id> offset_x <number>
/cssidebar set <id> offset_y <number>
/cssidebar set <id> width <80..220>
/cssidebar set <id> scale <0.5..2.0>
```

## Lines

```mcfunction
/cssidebar line add <id> text <style> <text>
/cssidebar line add <id> score <style> <label> <holder> <objective>
/cssidebar line add <id> timer <style> <label> <holder> <objective> <seconds|ticks>
/cssidebar line add <id> progress <style> <label> <current_holder> <current_objective> <max_holder> <max_objective>
/cssidebar line add <id> spacer
/cssidebar line remove <id> <index>
/cssidebar line clear <id>
```

Available text styles:

```text
normal, muted, accent, warning, critical, section
```

## Admin Tools

```mcfunction
/cssidebar list
/cssidebar info <id>
/cssidebar preview <id>
/cssidebar preview-theme <theme>
/cssidebar debug
```

## Resolver Rules

Custom Sidebars v1 displays one active sidebar per player.

Resolution order:

```text
exclusive filter > priority > layer > order > id
```

Layer order:

```text
event > mission > server > debug
```

`/cssidebar debug` is a forced local admin preview and does not follow normal sidebar competition.
