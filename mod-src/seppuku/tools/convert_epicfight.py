#!/usr/bin/env python3
"""Convert an Epic Fight armature animation (JSON, "attributes" format) into a
PlayerAnimator emote JSON (version 3) for the vanilla player model.

Usage:
    python3 tools/convert_epicfight.py <epicfight_anim.json> <out.json> [--start 0] [--end 2.9]

The Epic Fight biped has 20 joints; vanilla has 6 rigid parts with no elbows or
knees. Each vanilla part takes the orientation of its driving Epic Fight joint,
relative to the hip (Root). Arms and legs are re-aimed so a straight vanilla limb
points at where the Epic Fight hand/knee ends up. The held item gets its own
rotation and offset so the sword lands where the Epic Fight Tool_R joint puts it.
"""
import argparse
import json
import math

import numpy as np

# Blender-space Epic Fight joints (+X right, +Y front, +Z up, 1 unit = 16 px)
# -> vanilla model space (+X left, +Y down, +Z back, pixels).
C = np.array([[-1.0, 0, 0], [0, 0, -1.0], [0, -1.0, 0]])
# -> PlayerAnimator "body" space (+X right, +Y up, +Z back, blocks).
CP = np.array([[1.0, 0, 0], [0, 0, 1.0], [0, -1.0, 0]])
EF_TO_BLOCKS = 0.9375          # vanilla renders the 2-unit-tall model at 15/16 scale
BODY_PIVOT = np.array([0, 0.7, 0])  # PlayerAnimator rotates "body" around this point

# Vanilla default pivots (pixels) for each part, as PlayerAnimator defines them.
DEFAULT_PIVOT = {
    "head": (0, 0, 0), "torso": (0, 0, 0),
    "rightArm": (-5, 2, 0), "leftArm": (5, 2, 0),
    "rightLeg": (-1.9, 12, 0.1), "leftLeg": (1.9, 12, 0.1),
}
# part -> (orientation joint, anchor joint, aim) where aim is None or a
# (from, to) joint pair the part's long axis (model +Y) is swung to follow.
# Epic Fight naming: Hand_* is the forearm (starts at the elbow), Tool_* is the
# hand, Leg_* is the shin (starts at the knee), Torso is the waist.
PARTS = {
    "head": ("Head", "Head", None),
    "torso": ("Chest", "Torso", ("Head", "Torso")),
    "rightArm": ("Arm_R", "Arm_R", ("Arm_R", "Tool_R")),
    "leftArm": ("Arm_L", "Arm_L", ("Arm_L", "Tool_L")),
    "rightLeg": ("Thigh_R", "Thigh_R", ("Thigh_R", "Leg_R")),
    "leftLeg": ("Thigh_L", "Thigh_L", ("Thigh_L", "Leg_L")),
}
# Vanilla held-item grip point in arm-local pixels (from ItemInHandLayer).
GRIP = {"rightItem": np.array([-1.0, 10.0, -2.0]), "leftItem": np.array([1.0, 10.0, -2.0])}
ITEMS = {"rightItem": ("Tool_R", "rightArm"), "leftItem": ("Tool_L", "leftArm")}


def quat_to_mat(q):
    w, x, y, z = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


def euler_zyx(R):
    """Angles (x, y, z) such that R = Rz(z) @ Ry(y) @ Rx(x), matching ModelPart."""
    y = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    x = math.atan2(R[2, 1], R[2, 2])
    z = math.atan2(R[1, 0], R[0, 0])
    return np.array([x, y, z])


def swing(a, b):
    """Smallest rotation taking unit vector a onto unit vector b."""
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if c < -0.999999:
        axis = np.cross(a, [1, 0, 0])
        if np.linalg.norm(axis) < 1e-6:
            axis = np.cross(a, [0, 0, 1])
        axis /= np.linalg.norm(axis)
        return 2 * np.outer(axis, axis) - np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


class Rig:
    def __init__(self, data):
        self.rest, self.parent = {}, {}

        def walk(node, parent=None):
            self.rest[node["name"]] = np.array(node["transform"]).reshape(4, 4)
            self.parent[node["name"]] = parent
            for child in node["children"]:
                walk(child, node["name"])

        for node in data["armature"]["hierarchy"]:
            walk(node)
        self.anim = {a["name"]: a for a in data["animation"]}
        self.length = max(a["time"][-1] for a in data["animation"])
        self.rest_world = self.pose(None)

    def _delta(self, name, t):
        m = np.eye(4)
        a = self.anim.get(name)
        if a is None or t is None:
            return m
        times = a["time"]
        i = int(np.searchsorted(times, t))
        if i <= 0:
            f = a["transform"][0]
        elif i >= len(times):
            f = a["transform"][-1]
        else:  # pick the nearer sample; source is 60 fps so this is plenty
            i = i if times[i] - t < t - times[i - 1] else i - 1
            f = a["transform"][i]
        m[:3, :3] = quat_to_mat(f["rot"]) * np.array(f["sca"])
        m[:3, 3] = f["loc"]
        return m

    def pose(self, t):
        world = {}

        def get(name):
            if name not in world:
                local = self.rest[name] @ self._delta(name, t)
                p = self.parent[name]
                world[name] = local if p is None else get(p) @ local
            return world[name]

        for name in self.rest:
            get(name)
        return world


def to_model(v):
    """Epic Fight position (rest frame) -> vanilla model pixels."""
    return np.array([-16 * v[0], 24 - 16 * v[2], -16 * v[1]])


def convert(rig, start, end, keep_horizontal):
    rest = rig.rest_world
    root_rest = rest["Root"]
    ticks = int(round((end - start) * 20))
    frames = []
    for tick in range(ticks + 1):
        w = rig.pose(start + tick / 20)
        d_root = w["Root"] @ np.linalg.inv(root_rest)
        inv_root = np.linalg.inv(d_root)
        frame = {}

        # Whole-body transform (hip motion), in PlayerAnimator body space.
        rb = CP @ d_root[:3, :3] @ CP.T
        root_rest_p = EF_TO_BLOCKS * (CP @ root_rest[:3, 3])
        root_now_p = EF_TO_BLOCKS * (CP @ w["Root"][:3, 3])
        if not keep_horizontal:  # keep the hips under the first-person camera
            root_now_p[0], root_now_p[2] = root_rest_p[0], root_rest_p[2]
        t_b = root_now_p - BODY_PIVOT - rb @ (root_rest_p - BODY_PIVOT)
        frame["body"] = (t_b, euler_zyx(rb))

        local_rot = {}
        def rel(name):  # joint rotation relative to rest, in hip-local model space
            dj = w[name][:3, :3] @ np.linalg.inv(rest[name][:3, :3])
            return C @ inv_root[:3, :3] @ dj @ C.T
        def pos(name, src=None):  # joint position in hip-local model pixels
            m = (src or w)[name]
            p = m[:3, 3] if src else (inv_root @ np.append(m[:3, 3], 1))[:3]
            return to_model(p)

        for part, (rot_j, pivot_j, aim) in PARTS.items():
            R = rel(rot_j)
            n0 = np.array(DEFAULT_PIVOT[part], dtype=float)
            if aim:
                want = pos(aim[1]) - pos(aim[0])
                want /= np.linalg.norm(want)
                have = R @ np.array([0.0, 1.0, 0.0])
                R = swing(have, want) @ R
            p = pos(pivot_j) + R @ (n0 - pos(pivot_j, rest))
            local_rot[part] = (R, p)
            frame[part] = (p, euler_zyx(R))

        for item, (tool_j, arm) in ITEMS.items():
            R_arm, p_arm = local_rot[arm]
            n_arm = np.array(DEFAULT_PIVOT[arm], dtype=float)
            D = rel(tool_j)
            R_i = R_arm.T @ D
            grip_rest = n_arm + GRIP[item]
            grip_now = pos(tool_j) + D @ (grip_rest - pos(tool_j, rest))
            target_local = R_arm.T @ (grip_now - p_arm)
            offset = target_local - R_i @ GRIP[item]
            frame[item] = (offset, euler_zyx(R_i))
        frames.append(frame)

    # Unwrap angles so linear interpolation never spins the long way round.
    for part in frames[0]:
        angles = np.unwrap(np.array([f[part][1] for f in frames]), axis=0)
        for f, a in zip(frames, angles):
            f[part] = (f[part][0], a)
    return frames


def emote_json(frames, name, description, author):
    moves = []
    for tick, frame in enumerate(frames):
        move = {"tick": tick, "easing": "LINEAR"}
        for part, (p, r) in frame.items():
            move[part] = {
                "x": round(float(p[0]), 4), "y": round(float(p[1]), 4), "z": round(float(p[2]), 4),
                "pitch": round(float(r[0]), 4), "yaw": round(float(r[1]), 4), "roll": round(float(r[2]), 4),
            }
        moves.append(move)
    # Repeat the last pose one tick later and loop on it, so the final pose holds
    # until the mod stops the animation.
    last = dict(moves[-1], tick=len(frames))
    moves.append(last)
    end = len(frames)
    return {
        "version": 3,
        "name": name,
        "description": description,
        "author": author,
        "emote": {
            "beginTick": 0,
            "endTick": end,
            "stopTick": end + 1,
            "isLoop": True,
            "returnTick": end - 1,
            "nsfw": False,
            "degrees": False,
            "moves": moves,
        },
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source")
    ap.add_argument("out")
    ap.add_argument("--start", type=float, default=0.0, help="seconds")
    ap.add_argument("--end", type=float, default=2.9, help="seconds; source is 4.68 s long")
    ap.add_argument("--keep-horizontal", action="store_true",
                    help="keep the hips' forward/sideways travel (off by default for first person)")
    ap.add_argument("--name", default="seppuku")
    args = ap.parse_args()

    with open(args.source) as fh:
        rig = Rig(json.load(fh))
    frames = convert(rig, args.start, min(args.end, rig.length), args.keep_horizontal)
    out = emote_json(
        frames, args.name,
        "Converted from EpicFight - Dawnday's sepukku animation (Nexo, sidtheplayer), used with permission.",
        "Nexo, sidtheplayer",
    )
    with open(args.out, "w") as fh:
        json.dump(out, fh, indent=1)
    print(f"wrote {len(frames)} ticks to {args.out}")


if __name__ == "__main__":
    main()
