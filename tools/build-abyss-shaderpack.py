"""Build the local FantasyTruth companion pack from Bliss 2.1.0."""

import argparse
from pathlib import Path
import re
from tempfile import NamedTemporaryFile
from zipfile import ZIP_DEFLATED, ZipFile


EDGE_FOLDER = "world_abyss_edge"
EDGE_DEFINE = "#define FANTASYTRUTH_EDGE"
VOLUME = "shaders/lib/overworld_fog.glsl"
EDGE_INCLUDE = "/lib/fantasytruth_edge.glsl"
PACK_NAME = "FantasyTruth Abyss - Bliss (Chocapic13' Shaders edit).zip"


def text(pack, name):
    return pack.read(name).decode("utf-8").replace("\r\n", "\n")


def patch_fog(source):
    signature = "\nvec4 GetVolumetricFog(\n"
    body = "\t#ifndef TOGGLE_VL_FOG\n"
    if source.count(signature) != 1:
        raise ValueError("The Bliss volumetric fog entry point has changed.")
    start = source.index(signature) + 1
    offset = source.index(body, start)
    guarded = (
        "\t#ifdef FANTASYTRUTH_EDGE\n"
        "\t\tfloat edgeWeight = ftShaft(cameraPosition);\n"
        "\t\tif (edgeWeight > 0.0) return ftEdgeVolume(viewPosition, LightColor, AmbientColor, edgeWeight);\n"
        "\t#endif\n"
    )
    return (
        source[:start]
        + f'#ifdef FANTASYTRUTH_EDGE\n#include "{EDGE_INCLUDE}"\n#endif\n\n'
        + source[start:offset]
        + guarded
        + source[offset:]
    )


def medium_settings(source):
    for name, value in (("shadowMapResolution", "1024"), ("shadowDistance", "192.0")):
        source, count = re.subn(
            rf"(?m)^(const (?:int|float) {name}\s*=\s*)[\d.]+(?=\s*;)",
            rf"\g<1>{value}",
            source,
        )
        if count != 1:
            raise ValueError(f"The Bliss setting {name} has changed.")
    return source


def build(base, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if base.resolve() == target.resolve():
        raise ValueError("The destination must be distinct from the original Bliss ZIP.")
    if target.name != PACK_NAME:
        raise ValueError(f"Use the companion pack name {PACK_NAME!r}.")
    with ZipFile(base) as source:
        dimensions = text(source, "shaders/dimension.properties")
        if "dimension.world0" not in dimensions or "stratacubes:abyss/edge" in dimensions:
            raise ValueError("Expected an unmodified Bliss dimension configuration.")
        dimensions += f"\ndimension.{EDGE_FOLDER} = stratacubes:abyss/edge\n"
        fog = patch_fog(text(source, VOLUME))
        properties = text(source, "shaders/shaders.properties")
        properties = re.sub(
            r"(?m)^([ \t]*program\.world0/[^=\n]+=[^\n]+)$",
            lambda match: match.group(1) + "\n" + match.group(1).replace("program.world0/", f"program.{EDGE_FOLDER}/"),
            properties,
        )
        wrappers = {
            path: text(source, path)
            for path in source.namelist()
            if path.startswith("shaders/world0/") and path.endswith((".fsh", ".vsh", ".gsh", ".csh"))
        }
        if not wrappers or "LICENSE.md" not in source.namelist():
            raise ValueError("The Bliss world0 programs or original license are missing.")
        edits = {
            "shaders/dimension.properties": dimensions,
            "shaders/shaders.properties": properties,
            "shaders/lib/settings.glsl": medium_settings(text(source, "shaders/lib/settings.glsl")),
            VOLUME: fog,
        }
        glsl = Path(__file__).with_name("shaders") / "fantasytruth_edge.glsl"
        with NamedTemporaryFile(dir=target.parent, suffix=".zip", delete=False) as temporary:
            staged = Path(temporary.name)
        try:
            with ZipFile(staged, "w", ZIP_DEFLATED) as output:
                for entry in source.infolist():
                    if not entry.is_dir():
                        content = edits.get(entry.filename)
                        output.writestr(entry.filename, content if content is not None else source.read(entry))
                for path, program in wrappers.items():
                    shader = re.sub(r"(?m)^(#version[^\n]*\n)", rf"\1{EDGE_DEFINE}\n", program, count=1)
                    if EDGE_DEFINE not in shader:
                        raise ValueError(f"No GLSL version in {path}.")
                    output.writestr(path.replace("/world0/", f"/{EDGE_FOLDER}/"), shader)
                output.write(glsl, f"shaders{EDGE_INCLUDE}")
                output.writestr(
                    "FANTASYTRUTH-EDITS.md",
                    "# FantasyTruth Abyss\n\n"
                    "Companion edit of Bliss by Xonk, based on Chocapic13's Shaders. "
                    "The original credit and license files are retained.\n\n"
                    "FantasyTruth adds the Edge of the Abyss dimension route, local cloud sheets "
                    "and shadowed light shafts. Other dimensions keep the Bliss rendering programs. "
                    "The default shadow map is 1024 pixels with a 192-block radius.\n",
                )
            staged.replace(target)
        finally:
            staged.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bliss", type=Path, help="Local, unmodified Bliss 2.1.0 ZIP")
    parser.add_argument("--output", type=Path, default=Path("build/distributions") / PACK_NAME)
    options = parser.parse_args()
    build(options.bliss, options.output)
    print(options.output.resolve())
