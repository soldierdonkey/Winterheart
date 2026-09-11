import os
from pathlib import Path

# Only authentic 1950s and pre-1950s historical weapons are preserved
ALLOWED_GUNS = {
    "ak47",
    "db_long",
    "db_short",
    "fn_fal",
    "hk_g3",
    "kar98",
    "m1911",
    "m870",
    "rpk",
    "springfield1873",
    "uzi",
}

# Directories mapped directly from the TACZ file structure
TARGET_DIRECTORIES = [
    Path("data/tacz/index/guns"),
    Path("data/tacz/recipes/gun"),
]


def purge_non_1950s_guns(dry_run: bool = False):
    removed_count = 0
    kept_count = 0

    for target_dir in TARGET_DIRECTORIES:
        if not target_dir.exists():
            print(f"[!] Directory not found: {target_dir}")
            continue

        print(f"\nScanning: {target_dir}")
        for file_path in target_dir.glob("*.json"):
            gun_id = file_path.stem.lower()

            if gun_id not in ALLOWED_GUNS:
                if dry_run:
                    print(f"  [WOULD DELETE] {file_path.name}")
                else:
                    file_path.unlink()
                    print(f"  [-] Deleted: {file_path.name}")
                removed_count += 1
            else:
                print(f"  [+] Kept: {file_path.name}")
                kept_count += 1

    mode = "Dry run completed." if dry_run else "Deletion completed."
    print(f"\n{mode} Removed: {removed_count} files | Retained: {kept_count} files.")


if __name__ == "__main__":
    # Set dry_run=True to inspect what would be deleted without deleting
    purge_non_1950s_guns(dry_run=False)