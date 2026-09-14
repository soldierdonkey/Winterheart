# Custom Sidebars Toolkit

This datapack is a ready-to-run command toolkit for the **Custom Sidebars** Forge mod.

It does not replace the mod. It provides examples, test functions, and copyable command patterns for server owners, datapack creators, and map makers.

## Requirements

- Minecraft Java 1.20.1
- Forge 47.4.10 or compatible
- Custom Sidebars installed on both server and client

## Quick Start

Install this datapack in your world's `datapacks` folder, run `/reload`, then run:

```mcfunction
/function css_toolkit:help
```

Recommended first tests:

```mcfunction
/function css_toolkit:event/start_blackout
/function css_toolkit:mission/objectives
/function css_toolkit:server/status
/function css_toolkit:themes/gallery
/function css_toolkit:conflict/priority_demo
/function css_toolkit:admin/debug_overlay
```

Clear toolkit sidebars:

```mcfunction
/function css_toolkit:clear
```

## What This Toolkit Demonstrates

- Event sidebars with high priority and exclusive behavior.
- Mission sidebars for adventure maps.
- Persistent server-status panels.
- Scoreboard-backed score, timer, and progress lines.
- Theme previews for all built-in styles.
- Priority conflict resolution.
- Admin debugging tools.

## Built-In Themes

- `military_classic`
- `military_modern`
- `technological`
- `emergency_red`
- `rpg_souls`

## Notes

- Custom Sidebars v1 renders one active custom sidebar per player.
- `order` is only a tie-breaker in v1.
- `exclusive=true` acts as a filter before priority is evaluated.
- Timers and progress bars are scoreboard-driven so datapacks remain in full control.
