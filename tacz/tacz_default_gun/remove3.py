import os
from pathlib import Path

# Only ammunition for the retained 1950s/vintage firearms
ALLOWED_AMMO = {
    "12g",      # M870, Double-Barrels
    "308",      # FN FAL, HK G3 (7.62x51mm NATO)
    "45_70",    # Springfield 1873
    "45acp",    # M1911
    "762x39",   # AK-47, RPK
    "792x57",   # Kar98k (8mm Mauser)
    "9mm",      # Uzi
}

# Directories matching the TACZ index and recipe layout
TARGET_DIRECTORIES = [
    Path("data/tacz/index/ammo"),
    Path("data/tacz/recipes/ammo"),
]


def purge_unused_ammo(dry_run: bool = False):
    removed_count = 0
    kept_count = 0

    for target_dir in TARGET_DIRECTORIES:
        if not target_dir.exists():
            print(f"[!] Directory not found: {target_dir}")
            continue

        print(f"\nScanning: {target_dir}")
        for file_path in target_dir.glob("*.json"):
            ammo_id = file_path.stem.lower()

            if ammo_id not in ALLOWED_AMMO:
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
    # Toggle dry_run=True to inspect what files match before deletion
    purge_unused_ammo(dry_run=False)