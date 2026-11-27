#!/usr/bin/env python3
"""Writes the carcass data that winterheart_butchery shows in JEI.

Loot tables never reach the client, so the drops are copied from Butchery's jar (or a datapack override) into the
mod's assets. Rerun after updating Butchery, overriding one of these loot tables or changing config/Butchery.toml,
then rebuild the mod. Run from the instance root:

    python3 python/butchery_jei/gen_carcass_data.py
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/jei/carcasses.json"
BLEED_SECONDS = 45  # hard-coded in Butchery; see docs/features/carcass-butchering.md

# name, tool, loot table, organ table, drained blockstate of the hanging carcass afterwards (None: carcass gone)
COW_STEPS = [
    ("head", "cleaver", "cow_head_drop", None, 2),
    ("skin", "skinning_knife", "cow_skin_drop", None, 3),
    ("cut_1", "cleaver", "cow_cut_1_drop", "organs_drop_1", 4),
    ("cut_2", "cleaver", "cow_cut_2_drop", "organs_drop_2", 5),
    ("cut_3", "cleaver", "cow_cut_3_drop", "organs_drop_3", None),
]


def config_flag(text, key):
    match = re.search(rf'^\s*"?{re.escape(key)}"?\s*=\s*(true|false)', text, re.M)
    if not match:
        raise SystemExit(f'"{key}" not found in config/Butchery.toml')
    return match.group(1) == "true"


def loot_table(jar, name):
    path = f"data/butchery/loot_tables/blocks/{name}.json"
    overrides = sorted(ROOT.glob(f"datapacks/*/{path}"))
    if len(overrides) > 1:
        raise SystemExit(f"{path} is overridden by several datapacks: {overrides}")
    return json.loads(overrides[0].read_text() if overrides else jar.read(path))


def drops(table, organ):
    """One entry per item, adding up the pools. Fortune is ignored: Butchery rolls these without a tool."""
    totals = {}
    for pool in table["pools"]:
        entries = pool["entries"]
        if pool["rolls"] != 1 or len(entries) != 1 or entries[0]["type"] != "minecraft:item" or "conditions" in pool:
            raise SystemExit(f"unsupported loot pool, extend this script: {pool}")
        low = high = 1
        for function in entries[0].get("functions", []):
            if function["function"].removeprefix("minecraft:") == "set_count":
                count = function["count"]
                low, high = (count["min"], count["max"]) if isinstance(count, dict) else (count, count)
        total = totals.setdefault(entries[0]["name"], [0, 0])
        total[0] += int(low)
        total[1] += int(high)
    return [{"item": item, "min": low, "max": high, "organ": organ} for item, (low, high) in totals.items()]


def main():
    config = (ROOT / "config/Butchery.toml").read_text()
    jars = sorted(ROOT.glob("mods/butchery-*.jar"))
    if len(jars) != 1:
        raise SystemExit(f"expected one Butchery jar in mods/, found {jars}")
    with zipfile.ZipFile(jars[0]) as jar:
        steps = []
        for name, tool, table, organs, state_after in COW_STEPS:
            found = drops(loot_table(jar, table), False)
            if organs and config_flag(config, "Organs"):
                found += drops(loot_table(jar, organs), True)
            step = {"name": name, "tool": tool, "drops": found}
            if state_after is not None:
                step["state_after"] = state_after
            steps.append(step)
    cow = {
        "id": "cow",
        "entity": "minecraft:cow",
        "fresh": {"block": "butchery:cow_carcass", "state": 1},
        "drained": {"block": "butchery:drained_cow_carcass", "state": 1},
        "bleed_seconds": 0 if config_flag(config, "Instant Bleeding") else BLEED_SECONDS,
        "supports": ["butchery:hook", "butchery:rope"],
        "steps": steps,
    }
    OUT.write_text(json.dumps({"source": jars[0].name, "animals": [cow]}, indent=2) + "\n")
    print(f"wrote {OUT.relative_to(ROOT)} from {jars[0].name}")


if __name__ == "__main__":
    main()
