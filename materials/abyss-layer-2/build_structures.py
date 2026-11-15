"""Build the Layer 2 reference structures as a Java Edition 1.20.1 datapack."""
import gzip
import json
import math
import random
import struct
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

HERE = Path(__file__).resolve().parent
NAMESPACE = "stratacubes_l2_materials"
VERSION = 3465


def string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def number(value):
    return 3, struct.pack(">i", value)


def name(value):
    return 8, string(value)


def array(kind, values):
    return 9, struct.pack(">Bi", kind, len(values)) + b"".join(data for tag, data in values)


def compound(values):
    return 10, b"".join(bytes([tag]) + string(key) + data for key, (tag, data) in values.items()) + b"\0"


def block(name, **properties):
    return name if ":" in name else "minecraft:" + name, tuple(sorted(properties.items()))


def coordinates(x, y, z):
    return array(3, [number(x), number(y), number(z)])


class Structure:
    def __init__(self, x, y, z):
        self.size = (x, y, z)
        self.blocks = {}

    def put(self, x, y, z, state):
        if not all(0 <= at < bound for at, bound in zip((x, y, z), self.size)):
            raise ValueError((x, y, z, self.size))
        self.blocks[x, y, z] = state

    def box(self, lo, hi, state):
        for x in range(lo[0], hi[0] + 1):
            for y in range(lo[1], hi[1] + 1):
                for z in range(lo[2], hi[2] + 1):
                    self.put(x, y, z, state)

    def rock(self, sections, rng):
        for (cx, cy, cz), (rx, ry, rz) in sections:
            for x in range(cx - rx, cx + rx + 1):
                for y in range(cy - ry, cy + ry + 1):
                    for z in range(cz - rz, cz + rz + 1):
                        distance = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                        if distance < 0.78 + rng.random() * 0.23:
                            state = block(rng.choices(["tuff", "andesite", "mossy_cobblestone"], [6, 2, 1])[0])
                            self.put(x, y, z, state)

    def nbt(self):
        palette = sorted(set(self.blocks.values()))
        indices = {state: index for index, state in enumerate(palette)}
        states = []
        for key, options in palette:
            fields = {"Name": name(key)}
            if options:
                fields["Properties"] = compound({key: name(value) for key, value in options})
            states.append(compound(fields))
        voxels = [
            compound({"pos": coordinates(*pos), "state": number(indices[state])})
            for pos, state in sorted(self.blocks.items(), key=lambda pair: (pair[0][1], pair[0][0], pair[0][2]))
        ]
        _, root = compound({"DataVersion": number(VERSION), "size": coordinates(*self.size),
                            "palette": array(10, states), "blocks": array(10, voxels), "entities": array(10, [])})
        return gzip.compress(b"\x0a\x00\x00" + root, mtime=0)


def nest():
    rng = random.Random(105)
    plan = Structure(25, 15, 25)
    plan.rock([((12, 5, 17), (10, 5, 7)), ((12, 7, 11), (8, 3, 8)),
               ((8, 6, 19), (6, 4, 5)), ((17, 5, 21), (6, 4, 3))], rng)
    for z in range(7, 20):
        for x in range(5, 20):
            angle = math.atan2(z - 13, x - 12)
            radius = math.hypot(x - 12, (z - 13) * 1.20)
            rim = 6.9 + 0.44 * math.sin(7 * angle)
            if radius >= rim:
                continue
            plan.put(x, 9, z, block("muddy_mangrove_roots"))
            wood = "dark_oak_wood" if rng.random() < 0.45 else "mangrove_roots"
            plan.put(x, 10, z, block(wood))
            if radius > rim - 2.2:
                plan.put(x, 11, z, block("mangrove_roots"))
                if radius > rim - 1.15 and rng.randrange(3):
                    plan.put(x, 12, z, block("dark_oak_wood", axis=rng.choice("xz")))
    # Clear the approach through the bowl rim; omitted cells preserve the surrounding terrain on placement.
    for x in range(11, 14):
        for z in range(16, 23):
            plan.put(x, 9, z, block("rooted_dirt"))
            if (x, 10, z) not in plan.blocks:
                plan.put(x, 10, z, block("coarse_dirt"))
            for y in (11, 12):
                plan.blocks.pop((x, y, z), None)
    for x, z in ((9, 11), (14, 10), (8, 13), (16, 14)):
        plan.put(x, 11, z, block("white_carpet"))
    for x, z in ((12, 12), (13, 13)):
        plan.put(x, 11, z, block("bone_block", axis="x"))
    return plan


def traverse():
    rng = random.Random(206)
    plan = Structure(35, 22, 17)
    plan.rock([((6, 7, 8), (6, 6, 7)), ((29, 10, 8), (5, 6, 8)),
               ((16, 18, 8), (11, 3, 7)), ((25, 19, 9), (8, 2, 6))], rng)
    for x in range(5, 31):
        center = 8 + round(2 * math.sin((x - 6) * 0.22))
        y = 11 + min(3, max(0, (x - 10) // 5))
        for z in range(center - 2, center + 3):
            if abs(z - center) == 2 and rng.randrange(4) == 0:
                continue
            plan.put(x, y, z, block("dark_oak_wood", axis="x"))
            if abs(z - center) == 2 or (x + z) % 7 == 1:
                plan.put(x, y - 1, z, block("mangrove_roots"))
        if x in (12, 17, 23):
            for height in range(y + 1, 20):
                plan.put(x, height, center - 2, block("dark_oak_log"))
        if x in (10, 16, 26):
            plan.put(x, y, center + 2, block("stratacubes:glow_moss"))
            if (x, y - 1, center + 2) in plan.blocks:
                for height in range(y - 4, y - 1):
                    plan.put(x, height, center + 2, block("stratacubes:l2_upside_vine"))

    # Back the west-facing ladder with solid blocks and join it to the deck.
    for y in range(4, 12):
        plan.put(5, y, 7, block("dark_oak_log"))
        plan.put(4, y, 7, block("ladder", facing="west", waterlogged="false"))
    plan.put(4, 3, 7, block("tuff"))
    return plan


def waterfall():
    rng = random.Random(407)
    plan = Structure(31, 27, 31)
    plan.rock([((8, 16, 19), (8, 10, 10)), ((22, 13, 21), (8, 12, 9)),
               ((15, 4, 18), (10, 4, 12)), ((16, 20, 24), (10, 6, 6))], rng)
    for z in range(7, 26):
        for x in range(5, 26):
            radius = ((x - 15) / 10) ** 2 + ((z - 17) / 9) ** 2
            if radius > 1:
                continue
            plan.put(x, 4, z, block("tuff"))
            plan.put(x, 5, z, block("water", level="0") if radius < 0.68 else block("cobbled_deepslate"))
    for y in range(6, 25):
        center = 15 + (1 if y >= 15 else 0)
        # The water connects to the pool and fans out toward the cliff lip.
        for x in range(center - (2 if y < 12 else 1), center + (3 if y < 12 else 2)):
            for z in range(16, 19):
                plan.put(x, y, z, block("stratacubes:l2_updraft"))
    for x, z in ((8, 14), (21, 19), (15, 9), (19, 10)):
        plan.put(x, 4, z, block("tuff"))
        plan.put(x, 5, z, block("stratacubes:glow_moss"))
    return plan


def main():
    structures = {"weeper_nest": nest(), "inverted_traverse": traverse(), "heavens_waterfall": waterfall()}
    with ZipFile(HERE / "stratacubes-layer-2-structures.zip", "w", compression=ZIP_DEFLATED) as archive:
        archive.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15, "description": "StrataCubes Layer 2 structure templates"}}))
        for key, template in structures.items():
            archive.writestr(f"data/{NAMESPACE}/structures/{key}.nbt", template.nbt())
            print(f"{NAMESPACE}:{key} {template.size}: {len(template.blocks)} blocks")


if __name__ == "__main__":
    main()
