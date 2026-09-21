from pathlib import Path
from PIL import Image

# -------------------------------------------------------------------------
# 1. AUTHENTIC MINECRAFT 5-TONE PALETTES (1.20+)
# 0: Outline / Dark crevice | 1: Deep shadow | 2: Midtone | 3: Light | 4: Highlight
# -------------------------------------------------------------------------
PALETTES = {
    "iron": {
        0: (54, 57, 62, 255),
        1: (109, 117, 127, 255),
        2: (156, 163, 175, 255),
        3: (209, 213, 219, 255),
        4: (255, 255, 255, 255),
    },
    "gold": {
        0: (89, 58, 14, 255),
        1: (163, 114, 18, 255),
        2: (234, 173, 35, 255),
        3: (250, 226, 78, 255),
        4: (255, 254, 186, 255),
    },
    "copper": {
        0: (78, 38, 30, 255),
        1: (133, 62, 47, 255),
        2: (189, 87, 61, 255),
        3: (224, 126, 96, 255),
        4: (247, 180, 153, 255),
    },
    "diamond": {
        0: (14, 73, 82, 255),
        1: (28, 134, 140, 255),
        2: (46, 189, 173, 255),
        3: (132, 237, 222, 255),
        4: (222, 255, 250, 255),
    },
}

# -------------------------------------------------------------------------
# 2. 16x16 SILHOUETTE TEMPLATES
# '#' or '1' = auto-shade | '.' or '0' = transparent
# You can also use '0', '1', '2', '3', '4' in the grid for manual pixel overrides!
# -------------------------------------------------------------------------
TEMPLATES = {
    "gear": """
    ......#.#.#.....
    ...#.######.#...
    .#.##########.#.
    ..############..
    ###############.
    .####..##..#####
    ####...##...###.
    .###############
    ###############.
    .###...##...####
    #####..##..####.
    .###############
    ..############..
    .#.##########.#.
    ...#.######.#...
    .....#.#.#......
    """,
    "ring": """
    ................
    .....######.....
    ...##########...
    ..############..
    .##############.
    .####......####.
    .###........###.
    .###........###.
    .###........###.
    .###........###.
    .####......####.
    .##############.
    ..############..
    ...##########...
    .....######.....
    ................
    """,
    "rod": """
    .............###
    ............####
    ...........#####
    ..........#####.
    .........#####..
    ........#####...
    .......#####....
    ......#####.....
    .....#####......
    ....#####.......
    ...#####........
    ..#####.........
    .#####..........
    #####...........
    ####............
    ###.............
    """,
    "plate": """
    ................
    ...##########...
    ..############..
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    .##############.
    ..############..
    ...##########...
    ................
    """,
    "screw": """
    ................
    ....########....
    ...##########...
    ...###.##.###...
    ....########....
    ......####......
    .....#####......
    ......#####.....
    .....#####......
    ......#####.....
    .....#####......
    ......#####.....
    ......####......
    .......##.......
    ................
    ................
    """,
    "bolt": """
    ................
    ................
    ...........#....
    ..........###...
    .........#####..
    ........#####...
    .......#####....
    ......#####.....
    .....#####......
    ....#####.......
    ...#####........
    ..#####.........
    ...###..........
    ....#...........
    ................
    ................
    
    """,
}


# -------------------------------------------------------------------------
# 3. AUTO-SHADING ENGINE
# Simulates top-left directional illumination, rim shadows, and specular highlights
# -------------------------------------------------------------------------
def parse_grid(raw_input):
    """Normalizes either an ASCII multiline string or a 2D list into a 16x16 matrix."""
    if isinstance(raw_input, str):
        lines = [line.strip() for line in raw_input.strip().splitlines() if line.strip()]
        return [[char for char in line] for line in lines]
    return raw_input


def shade_pixel(r, c, grid):
    """Calculates the Minecraft tone index (0 to 4) for a solid pixel."""
    val = str(grid[r][c])

    # Transparent
    if val in (".", " ", "0", "None"):
        return None

    # Manual tone override (if you typed 0, 1, 2, 3, or 4 directly)
    if val in ("0", "1", "2", "3", "4") and val != "0":
        return int(val)

    def is_empty(y, x):
        if not (0 <= y < 16 and 0 <= x < 16):
            return True
        return str(grid[y][x]) in (".", " ", "0", "None")

    # Directional empty neighbor checks
    top = is_empty(r - 1, c)
    bottom = is_empty(r + 1, c)
    left = is_empty(r, c - 1)
    right = is_empty(r, c + 1)

    tl = is_empty(r - 1, c - 1)
    tr = is_empty(r - 1, c + 1)
    bl = is_empty(r + 1, c - 1)
    br = is_empty(r + 1, c + 1)

    is_border = top or bottom or left or right

    # Outward surface normal vector (pointing toward empty space)
    ny = (int(bottom) - int(top)) + 0.5 * (int(bl) + int(br) - int(tl) - int(tr))
    nx = (int(right) - int(left)) + 0.5 * (int(tr) + int(br) - int(tl) - int(bl))

    # Dot product with top-left light source:
    # High positive = faces top-left (light) | High negative = faces bottom-right (shadow)
    light_dot = -(nx + ny)

    # Global diagonal position (Minecraft items are globally brighter toward top-left)
    diag_pos = 1.0 - (r + c) / 30.0

    # Thin pixel check (features 1-2px wide shouldn't be overwhelmed with black outline)
    is_thin = (top and bottom) or (left and right) or (tl and br) or (tr and bl)

    if is_thin:
        if light_dot > 0 or diag_pos > 0.55:
            return 3
        return 1

    # Outer perimeter logic
    if is_border:
        # Bottom and right edges always get dark outline
        if light_dot < -0.3 or bottom or right:
            return 0
        # Top and left outer edges get soft outline/mid tone to frame the item
        return 1 if diag_pos < 0.6 else 2

    # Interior pixels (1px inside the perimeter and beyond)
    # Immediately adjacent to top-left empty space -> Specular Highlight
    if tl or top or left:
        if light_dot > 0.4 and diag_pos > 0.35:
            return 4
        return 3

    # Adjacent to bottom-right empty space -> Shadow
    if br or bottom or right:
        return 1

    # Deep interior shading based on ambient diagonal lighting
    if diag_pos > 0.65:
        return 3
    elif diag_pos > 0.35:
        return 2
    return 1


def generate_item_image(grid_input, material="iron", upscale=16):
    """
    Renders a 16x16 image from a silhouette grid and returns a PIL Image.
    'upscale' creates an enlarged, crisp nearest-neighbor preview.
    """
    grid = parse_grid(grid_input)
    palette = PALETTES[material.lower()]

    img_16x16 = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    for r in range(16):
        for c in range(16):
            tone = shade_pixel(r, c, grid)
            if tone is not None:
                img_16x16.putpixel((c, r), palette[tone])

    # Optional scaling for easy viewing without external image tools
    if upscale > 1:
        img_preview = img_16x16.resize(
            (16 * upscale, 16 * upscale), resample=Image.Resampling.NEAREST
        )
        return img_16x16, img_preview

    return img_16x16, None


# -------------------------------------------------------------------------
# 4. DEMO RUNNER
# -------------------------------------------------------------------------
if __name__ == "__main__":
    output_dir = Path("textures/item")
    output_dir.mkdir(exist_ok=True)

    materials = ["copper", "iron", "gold", "diamond"]

    print("Generating Minecraft textures...")
    for item_name, template in TEMPLATES.items():
        for mat in materials:
            base_img, preview_img = generate_item_image(template, material=mat, upscale=16)

            # Save 16x16 (resource-pack ready)
            base_img.save(output_dir / f"{mat}_{item_name}.png")

            # Save 256x256 preview
            # preview_img.save(output_dir / f"{mat}_{item_name}_preview.png")

    print(f"Done! Generated 24 textures in '{output_dir.resolve()}'.")