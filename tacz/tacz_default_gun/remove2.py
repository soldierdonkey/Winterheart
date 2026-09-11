import os
from pathlib import Path

# Approved 1950s, WWII surplus, and vintage attachments
ALLOWED_ATTACHMENTS = {
    # Ammunition Types
    "ammo_mod_fmj",
    "mmo_mod_fmj",  # Handles the filename typo in the source pack
    "ammo_mod_he",
    "ammo_mod_hp",
    "ammo_mod_i",
    "ammo_mod_slug",
    # Bayonets & Barrel Fittings
    "bayonet_6h3",
    "muzzle_choke_sg",
    # Period Optics
    "scope_1873_6x",
    "scope_98k",
    "scope_retro_2x",
}

TARGET_DIRECTORIES = [
    Path("data/tacz/index/attachments"),
    Path("data/tacz/recipes/attachments"),
]


def purge_non_1950s_attachments(dry_run: bool = False):
    removed_count = 0
    kept_count = 0

    for target_dir in TARGET_DIRECTORIES:
        if not target_dir.exists():
            print(f"[!] Directory not found: {target_dir}")
            continue

        print(f"\nScanning: {target_dir}")
        for file_path in target_dir.glob("*.json"):
            attachment_id = file_path.stem.lower()

            if attachment_id not in ALLOWED_ATTACHMENTS:
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
    # Change to dry_run=True to inspect matches before deleting
    purge_non_1950s_attachments(dry_run=False)