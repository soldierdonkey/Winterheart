#!/usr/bin/env python3
"""Writes the Butchery data that winterheart_butchery shows in JEI.

carcasses.json beside this script lists each carcass's steps and loot tables, and processes.json the tanning, skull
crushing, acid, salt, blood, bone meal and cleaning rules. Loot tables never reach the client, so
the drops are copied from Butchery's jar (or a datapack override) into the mod's assets. Rerun after editing the
table, updating Butchery, overriding one of its loot tables or changing config/Butchery.toml, then rebuild the mod. Run from the instance root:

    python3 python/butchery_jei/gen_carcass_data.py
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/jei/carcasses.json"
# Which steps and loot tables each carcass has. Butchery hard-codes this, so it was read from the jar by hand and has
# to be rechecked after a Butchery update. "steps" is the carcass laid down; "hanging" is the same carcass on a hook,
# where "broken" names tables Butchery asks for under an ID that doesn't exist.
TABLE = Path(__file__).with_name("carcasses.json")
# The same for the other processes. Skull groups are in the order Butchery tries them.
PROCESSES = Path(__file__).with_name("processes.json")


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


def drops(jar, names, organ):
    """The [min, max, chance] of every item over these tables. Fortune is ignored: these are rolled without a tool."""
    totals = {}
    for name in names:
        for pool in loot_table(jar, name)["pools"]:
            if pool["rolls"] != 1 or "conditions" in pool:
                raise SystemExit(f"unsupported pool in {name}, extend this script: {pool}")
            choice = len(pool["entries"]) > 1  # one of several entries is picked, so none is guaranteed
            found = {}
            for entry in pool["entries"]:
                if entry["type"] != "minecraft:item" or "conditions" in entry:
                    raise SystemExit(f"unsupported entry in {name}, extend this script: {entry}")
                low = high = 1
                for function in entry.get("functions", []):
                    kind = function["function"].removeprefix("minecraft:")
                    if kind == "set_count":
                        count = function["count"]
                        low, high = (count["min"], count["max"]) if isinstance(count, dict) else (count, count)
                    elif kind != "apply_bonus":
                        raise SystemExit(f"unsupported function in {name}, extend this script: {function}")
                low, high = int(low), int(high)
                item = entry["name"]
                if item in found:
                    low, high = min(low, found[item][0]), max(high, found[item][1])
                found[item] = (low, high)
            for item, (low, high) in found.items():
                chance = choice and len(found) > 1
                total = totals.setdefault(item, [0, 0, False])
                total[0] += 0 if chance else low
                total[1] += high
                total[2] |= chance
    return [{"item": item, "min": low, "max": high, "chance": chance, "organ": organ}
            for item, (low, high, chance) in totals.items() if high > 0 and item != "minecraft:air"]


def step_name(tables):
    """The label of a step, from what its loot tables are called."""
    # the order matters: "armor" contains "arm"
    for word in ("head", "skin", "plucking", "armor", "arm", "wing", "leg", "spike", "heart", "stomach", "liver",
                 "intestine", "body", "fur"):
        if any(word in table for table in tables):
            return word
    return "cut"


def totals(steps):
    result = {}
    for step in steps:
        for drop in step:
            total = result.setdefault(drop["item"], [0, 0])
            total[0] += drop["min"]
            total[1] += drop["max"]
    return result


def amount(total):
    low, high = total
    return str(low) if low == high else f"{low}-{high}"


def processes(jar, organs, vampire):
    """processes.json with each skull group's loot tables turned into drops."""
    data = json.loads(PROCESSES.read_text())
    groups = []
    for group in data["skulls"]:
        if vampire and "vampire_tables" in group:
            found = drops(jar, group["vampire_tables"], False)
        else:
            found = drops(jar, group["tables"], False)
            if organs:
                found += drops(jar, group.get("organ_tables", []), True)
        groups.append({"id": group["id"], "tags": group["tags"], "exclude": group.get("exclude", []), "drops": found})
    tanning, acid = data["tanning"], data["acid"]
    tanning["seconds"] = tanning.pop("ticks") // 20
    acid["seconds"] = acid.pop("ticks") // 20
    salt = data["salt"]
    salt["interval_seconds"] = salt.pop("interval_ticks") // 20
    return {**data, "skulls": groups}


def main():
    config = (ROOT / "config/Butchery.toml").read_text()
    jars = sorted(ROOT.glob("mods/butchery-*.jar"))
    if len(jars) != 1:
        raise SystemExit(f"expected one Butchery jar in mods/, found {jars}")
    organs = config_flag(config, "Organs")
    instant = config_flag(config, "Instant Bleeding")
    farmers = config_flag(config, "Farmers Delight") and bool(list(ROOT.glob("mods/FarmersDelight-*.jar")))

    def step_drops(jar, step):
        # Butchery swaps in the _fd_compat table, where there is one, when its Farmer's Delight setting is on.
        names = step["tables"]
        compat = {n.removesuffix("_fd_compat") for n in names if n.endswith("_fd_compat")}
        names = [n for n in names if not (n in compat if farmers else n.endswith("_fd_compat"))]
        found = drops(jar, names, False)
        if organs:
            found += drops(jar, step.get("organ_tables", []), True)
        if "leaves" in step:
            found.append({"item": step["leaves"], "min": 1, "max": 1, "chance": False, "organ": False})
        return found

    vampire = config_flag(config, "Vampires Delight Eye")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    animals = []
    with zipfile.ZipFile(jars[0]) as jar:
        OUT.with_name("processes.json").write_text(json.dumps(processes(jar, organs, vampire), indent=1) + "\n")
        for entry in json.loads(TABLE.read_text()):
            animal = {key: value for key, value in entry.items() if key not in ("steps", "hanging", "bleed_ticks")}
            animal["bleeds"] = "drained" in entry
            # Blood is only listed where it differs from 1000 mB of ordinary blood.
            instant_mb = animal.pop("blood_mb_instant", None)
            if instant and instant_mb:
                animal["blood_mb"] = instant_mb
            animal["bleed_seconds"] = 0 if instant else entry.get("bleed_ticks", 0) // 20
            animal["steps"] = [{"name": step_name(step["tables"] or step.get("organ_tables", [])), "tool": step["tool"],
                                "clicks": step.get("clicks", 1), "drops": step_drops(jar, step)}
                               for step in entry["steps"]]
            laid = totals(step["drops"] for step in animal["steps"])
            hanging = entry.get("hanging")
            if hanging:
                if "leaves" in entry["steps"][-1]:
                    hanging[-1]["leaves"] = entry["steps"][-1]["leaves"]
                hung = totals(step_drops(jar, step) for step in hanging)
                animal["hanging_differences"] = [
                    {"item": item, "laid": amount(laid.get(item, [0, 0])), "hanging": amount(hung.get(item, [0, 0]))}
                    for item in dict.fromkeys([*laid, *hung]) if laid.get(item) != hung.get(item)]
            animals.append(animal)
    lines = ",\n".join(json.dumps(animal) for animal in animals)
    OUT.write_text(f'{{"source": "{jars[0].name}", "animals": [\n{lines}\n]}}\n')
    print(f"wrote {len(animals)} carcasses to {OUT.relative_to(ROOT)} from {jars[0].name}")


if __name__ == "__main__":
    main()
