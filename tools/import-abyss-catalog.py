"""Import the local abis catalog into the Minecraft 1.20.1 resource layout."""

import argparse
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/stratacubes"
DATA = RESOURCES / "data/stratacubes"
NAMESPACE = "stratacubes"
EXISTING_BLOCKS = set("""
abyss_stone l1_chalk orth_cobble orth_plaster orth_timber orth_slate orth_dock_plank
orth_crate orth_iron_bolt pier_platform cable orth_windmill_blade abyss_moss glow_moss
giant_log l1_windmill_base camp_supply_crate relic_ore
""".split())
ITEM_ALIASES = {
    "i_relic_shard": "relic_shard",
    "i_pickaxe": "delver_pickaxe",
    "i_depth_gauge": "depth_gauge",
}
WHITE_WHISTLES = {
    "bondrewd": ("波多尔多", "Bondrewd"), "lyza": ("莱莎", "Lyza"),
    "osen": ("奥森", "Ozen"), "srajo": ("斯拉乔", "Srajo"), "wakuna": ("瓦库纳", "Wakuna"),
}
EQUIPMENT = set("i_star_compass i_rope i_lantern i_oil i_bandage i_flag".split())
MATERIALS = set("i_bone i_crystal_shard i_narehate_flesh i_shroom_cap".split())

# The loot catalog has names for these items. Their textures are maintained in StrataCubes.
MATERIAL_NAMES = {
    "i_ring_fragment": ("环之碎片", "Ring Fragment"),
    "mat_amaranth_venom": ("镰足毒", "Amaranth Venom"),
    "mat_beak_plate": ("锤喙喙板", "Hammerbeak Plate"),
    "mat_chitin": ("甲壳", "Chitin"),
    "mat_emperor_shell": ("帝王壳板", "Emperor Shell Plate"),
    "mat_expedition_log": ("远征记录", "Expedition Log"),
    "mat_fang": ("分颚獠牙", "Splitjaw Fang"),
    "mat_feather": ("深渊羽毛", "Abyss Feather"),
    "mat_fin": ("鱼鳍", "Demonfish Fin"),
    "mat_fur": ("软毛", "Soft Fur"),
    "mat_glow_loot": ("发光遗失物", "Glowing Find"),
    "mat_gold_leaf": ("黄金箔", "Gold Leaf"),
    "mat_gorilla_pelt": ("厚皮", "Thick Pelt"),
    "mat_ink": ("墨囊", "Abyss Ink Sac"),
    "mat_iron_core": ("铁雨核", "Iron Rain Core"),
    "mat_kazura_egg": ("带刺卵", "Spined Egg"),
    "mat_lost_item": ("遗失的小物件", "Lost Trinket"),
    "mat_mail_balloon": ("邮件气球", "Mail Balloon"),
    "mat_map_scrap": ("地图残页", "Map Scrap"),
    "mat_meat": ("兽肉", "Raw Abyss Beast Meat"),
    "mat_needle": ("珠穿针", "Orb Piercer Quill"),
    "mat_paralyze_toxin": ("麻痹毒", "Paralyzing Toxin"),
    "mat_prism_eye": ("柄眼结晶", "Prism Eye Crystal"),
    "mat_pseudo_sac": ("伪水囊", "False Water Sac"),
    "mat_ration": ("探窟口粮", "Delving Ration"),
    "mat_rope": ("旧绳", "Worn Rope"),
    "mat_scale": ("分颚鳞皮", "Splitjaw Scale"),
    "mat_shell": ("珠穿甲片", "Orb Piercer Shell"),
    "mat_sickle_horn": ("镰角", "Sickle Horn"),
    "mat_silk_gland": ("丝腺", "Silk Gland"),
    "mat_specimen_jar": ("标本罐", "Specimen Jar"),
    "mat_squid_meat": ("乌贼肉", "Raw Abyss Squid"),
    "mat_stinger_tip": ("尾针", "Stinger Tip"),
    "mat_sun_crystal": ("日泡结晶", "Sun Bubble Crystal"),
    "mat_surgery_kit": ("手术器具", "Surgery Kit"),
    "mat_symbiotic_spore": ("共生孢子", "Symbiotic Spore"),
    "mat_tenmaku_film": ("幻象膜", "Tenmaku Film"),
    "mat_turbine_core": ("涡轮核", "Turbine Core"),
    "mat_turbine_shell": ("涡轮甲", "Turbine Shell"),
    "mat_venom_sac": ("毒囊", "Venom Sac"),
    "mat_voice_sac": ("鸣囊", "Voice Sac"),
    "mat_whistle_case": ("白笛匣", "Whistle Case"),
    "mat_wing_membrane": ("翼膜", "Wing Membrane"),
}


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def remap(value):
    if isinstance(value, str):
        return value.replace("mia:", NAMESPACE + ":")
    if isinstance(value, list):
        return [remap(item) for item in value]
    if isinstance(value, dict):
        return {key: remap(item) for key, item in value.items()}
    return value


def compatible_model(source):
    model = remap(source)
    model.pop("format_version", None)
    model.pop("texture_size", None)  # Blockbench already exports face UVs on a 0–16 scale.
    angles = (-45, -22.5, 0, 22.5, 45)
    for element in model.get("elements", []):
        rotation = element.pop("rotation", None)
        if not rotation:
            continue
        if "axis" in rotation:
            axis, angle = rotation["axis"], rotation["angle"]
        else:
            # Older Java models support one axis per cuboid.
            values = {axis: (rotation.get(axis, 0) + 180) % 360 - 180 for axis in "xyz"}
            axis = max(values, key=lambda axis: abs(values[axis]))
            angle = values[axis]
            if abs(angle) > 45:
                quarter_turns = round(angle / 90)
                rotate_element(element, axis, quarter_turns, rotation["origin"])
                angle -= quarter_turns * 90
        angle = min(angles, key=lambda allowed: abs(allowed - angle))
        if angle:
            element["rotation"] = {"axis": axis, "angle": angle, "origin": rotation["origin"]}
    return model


def rotate_element(element, axis, turns, origin):
    """Bake right-angle rotations into the cuboid so Minecraft can load the model."""
    axes = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}
    a, b = axes[axis]

    def rotate(point, pivot):
        p = list(point)
        for _ in range(turns % 4):
            p[a], p[b] = pivot[a] - (p[b] - pivot[b]), pivot[b] + (p[a] - pivot[a])
        return p

    corners = [rotate(element[key], origin) for key in ("from", "to")]
    element["from"] = [round(min(x, y), 4) for x, y in zip(*corners)]
    element["to"] = [round(max(x, y), 4) for x, y in zip(*corners)]
    vectors = {"east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
               "down": (0, -1, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
    names = {value: key for key, value in vectors.items()}
    faces = {}
    for direction, face in element["faces"].items():
        facing = names[tuple(rotate(vectors[direction], (0, 0, 0)))]
        if "cullface" in face:
            face["cullface"] = names[tuple(rotate(vectors[face["cullface"]], (0, 0, 0)))]
        faces[facing] = face
    element["faces"] = faces


def block_type(block):
    identifier, form = block["id"], block["render"]
    if identifier == "petrified_log":
        form = "pillar"
    if form == "transparent":
        form = "field"
    if form == "cross" or block["group"] == "flora":
        material = "plant"
    elif identifier in {"l5_bone_sand", "abyss_gravel"}:
        material = "earth"
    elif any(word in identifier for word in ("crystal", "window", "ring_stone")):
        material = "glass"
    elif any(word in identifier for word in ("plank", "bark", "trunk", "flag", "ladder", "signboard",
                                             "stand", "tent", "bedroll", "wheel", "rope")):
        material = "wood"
    elif any(word in identifier for word in ("gold", "iron", "bell", "hoist", "gondola", "generator", "stove")):
        material = "metal"
    else:
        material = "stone"
    return form, material


def self_drop(identifier):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NAMESPACE}:{identifier}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def import_blocks(source, blocks, language, block_rows):
    for block in blocks:
        identifier = block["id"]
        if identifier in EXISTING_BLOCKS:
            continue
        form, material = block_type(block)
        hardness = -1 if block["hard"] == "不可破坏" else 0 if form == "fluid" else float(block["hard"])
        block_rows.append(f'        add.accept("{identifier}", "{form}", "{material}", {float(hardness)}F, {block["light"]});')
        for lang, field in (("zh_cn", "cn"), ("en_us", "en")):
            language[lang][f"block.{NAMESPACE}.{identifier}"] = block[field]

        model_path = Path("models/block") / f"{identifier}.json"
        model = compatible_model(load(source / model_path))
        model.setdefault("parent", "minecraft:block/block")
        if identifier in {"golden_crystal_wall", "l6_crystal_shell"}:
            model["render_type"] = "minecraft:translucent"
        write(ASSETS / model_path, model)
        for value in model.get("textures", {}).values():
            if value.startswith(f"{NAMESPACE}:block/"):
                name = value.split(":", 1)[1] + ".png"
                copy_texture(source, name)

        state = remap(load(source / "blockstates" / f"{identifier}.json"))
        write(ASSETS / "blockstates" / f"{identifier}.json", state)
        if form == "fluid":
            bucket = identifier + "_bucket"
            write(ASSETS / "models/item" / f"{bucket}.json",
                  {"parent": "forge:item/bucket", "loader": "forge:fluid_container", "fluid": f"{NAMESPACE}:{identifier}"})
            language["zh_cn"][f"item.{NAMESPACE}.{bucket}"] = block["cn"] + "桶"
            language["en_us"][f"item.{NAMESPACE}.{bucket}"] = block["en"] + " Bucket"
        else:
            write(ASSETS / "models/item" / f"{identifier}.json", {"parent": f"{NAMESPACE}:block/{identifier}"})
            if hardness >= 0 and form != "field":
                write(DATA / "loot_tables/blocks" / f"{identifier}.json", self_drop(identifier))


def copy_texture(source, name):
    destination = ASSETS / "textures" / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes((source / "textures" / name).read_bytes())


def item_name(identifier, definitions):
    for base, (zh, en) in WHITE_WHISTLES.items():
        if identifier == "i_whistle_white_" + base:
            return "白笛·" + zh, "White Whistle · " + en
    if identifier in ("i_value_token_10", "i_value_token_100"):
        face = identifier.rsplit("_", 1)[-1]
        return "价值代币·" + face, "Value Token · " + face
    if identifier in ("i_demonfish_raw_b", "i_demonfish_raw_c"):
        variant = identifier[-1].upper()
        return "生恶魔鱼·" + variant, "Raw Demonfish · " + variant
    definition = definitions[identifier]
    return definition["cn"], definition["en"]


def import_items(source, definitions, language, item_rows):
    for path in sorted((source / "models/item").glob("i_*.json")):
        identifier = path.stem
        target = ITEM_ALIASES.get(identifier, identifier)
        if target != "i_lantern":  # StrataCubes has its own lantern geometry and textures.
            model = compatible_model(load(path))
            if "elements" in model:
                model.setdefault("parent", "minecraft:block/block")
                model["render_type"] = "minecraft:cutout"
            write(ASSETS / "models/item" / f"{target}.json", model)
            copy_texture(source, f"item/{identifier}.png")
            emissive = f"item/{identifier}_emissive.png"
            if (source / "textures" / emissive).is_file():
                copy_texture(source, emissive)
        if identifier in ITEM_ALIASES:
            continue
        cn, en = item_name(identifier, definitions)
        category = ("equipment" if identifier in EQUIPMENT or identifier.startswith("i_whistle_") else
                    "material" if identifier in MATERIALS or identifier.startswith(("i_demonfish_raw", "i_value_token")) else "relic")
        stacks = (1 if identifier.startswith("i_whistle_") or category == "relic"
                  else 16 if identifier in {"i_star_compass", "i_lantern", "i_oil", "i_bandage"} else 64)
        item_rows.append(f'        add.accept("{identifier}", "{category}", {stacks});')
        language["zh_cn"][f"item.{NAMESPACE}.{target}"] = cn
        language["en_us"][f"item.{NAMESPACE}.{target}"] = en

    for identifier, (cn, en) in MATERIAL_NAMES.items():
        if not (ASSETS / "textures/item" / f"{identifier}.png").is_file():
            raise FileNotFoundError(f"Missing StrataCubes item texture for {identifier}")
        category = "relic" if identifier == "i_ring_fragment" else "material"
        item_rows.append(f'        add.accept("{identifier}", "{category}", 64);')
        write(ASSETS / "models/item" / f"{identifier}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NAMESPACE}:item/{identifier}"}})
        language["zh_cn"][f"item.{NAMESPACE}.{identifier}"] = cn
        language["en_us"][f"item.{NAMESPACE}.{identifier}"] = en


def import_catalog(abis):
    specs = abis / "MC模组素材/data"
    source = abis / "assets/mia"
    blocks = load(specs / "blocks.json")["blocks"]
    definitions = {entry["id"]: entry for entry in load(specs / "models.json")["items"]}
    source_blocks = {path.stem for path in (source / "models/block").glob("*.json")}
    if {entry["id"] for entry in blocks} != source_blocks:
        raise ValueError("The block list and block models disagree")
    loot_ids = set(re.findall(r'"((?:mat_[a-z0-9_]+)|i_ring_fragment)"', (specs / "loot.json").read_text(encoding="utf-8")))
    if loot_ids != MATERIAL_NAMES.keys():
        raise ValueError("Update the material names to match the loot catalog")

    language = {lang: load(ASSETS / "lang" / f"{lang}.json") for lang in ("zh_cn", "en_us")}
    language["zh_cn"][f"itemGroup.{NAMESPACE}.abyss_materials"] = "阿比斯 · 材料与补给"
    language["en_us"][f"itemGroup.{NAMESPACE}.abyss_materials"] = "Abyss · Materials and Supplies"
    block_rows, item_rows = [], []
    import_blocks(source, blocks, language, block_rows)
    import_items(source, definitions, language, item_rows)
    for lang, entries in language.items():
        write(ASSETS / "lang" / f"{lang}.json", entries)
    for block in blocks:
        identifier = block["id"]
        form, material = block_type(block)
        if identifier in EXISTING_BLOCKS or block["hard"] in {"—", "不可破坏"} or form in {"field", "cross"}:
            continue
        tool = {"wood": "axe", "earth": "shovel", "stone": "pickaxe", "metal": "pickaxe"}.get(material)
        if tool:
            tag_path = RESOURCES / "data/minecraft/tags/blocks/mineable" / f"{tool}.json"
            tag = load(tag_path) if tag_path.exists() else {"replace": False, "values": []}
            key = f"{NAMESPACE}:{identifier}"
            if key not in tag["values"]:
                tag["values"].append(key)
                write(tag_path, tag)

    code = """package dev.fantasytruth.abyss;

/** Registry entries imported by tools/import-abyss-catalog.py from the local abis catalog. */
final class AbyssAssetCatalog {
    @FunctionalInterface
    interface BlockEntry { void accept(String id, String form, String material, float hardness, int light); }

    @FunctionalInterface
    interface ItemEntry { void accept(String id, String category, int stackSize); }

    static void blocks(BlockEntry add) {
%s
    }

    static void items(ItemEntry add) {
%s
    }

    private AbyssAssetCatalog() {}
}
""" % ("\n".join(block_rows), "\n".join(item_rows))
    (ROOT / "src/main/java/dev/fantasytruth/abyss/AbyssAssetCatalog.java").write_text(code, encoding="utf-8")
    print(f"{len(block_rows)} added blocks, {len(item_rows)} added standalone items, plus fluid buckets")


if __name__ == "__main__":
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("abis", nargs="?", type=Path, default=ROOT.parent / "abis")
    import_catalog(cli.parse_args().abis)
