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
EDGE_VIEW = "/lib/fantasytruth_edge_view.glsl"
EDGE_DEPTH = "/lib/fantasytruth_edge_depth.glsl"
EDGE_BOUNDS = "/lib/fantasytruth_edge_bounds.glsl"
EDGE_UPSAMPLE = "/lib/fantasytruth_edge_upsample.glsl"
LIGHTING = "shaders/dimensions/composite1.fsh"
CLOUDS = "shaders/dimensions/composite2.fsh"
FOG_COMPOSITE = "shaders/dimensions/composite3.fsh"
LOD_TERRAIN = "shaders/dimensions/DH_solid.fsh"
PACK_NAME = "FantasyTruth Abyss - Bliss (Chocapic13' Shaders edit).zip"


def text(pack, name):
    return pack.read(name).decode("utf-8").replace("\r\n", "\n")


def patch_fog(source):
    signature = "\nvec4 GetVolumetricFog(\n"
    if source.count(signature) != 1:
        raise ValueError("The Bliss volumetric fog entry point has changed.")
    start = source.index(signature) + 1
    return (
        source[:start]
        + f'#if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH\n#include "{EDGE_INCLUDE}"\n#endif\n\n'
        + source[start:]
    )


def patch_lighting(source):
    hooks = {
        "void main() {": (f'#if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH\n'
                          f'#include "{EDGE_VIEW}"\n#include "{EDGE_BOUNDS}"\n#endif\n\nvoid main() {{'),
        "gl_FragData[0].rgb = clamp(fp10Dither(Background, triangularize(noise_2)), 0.0, 65000.);": (
            "#ifdef FANTASYTRUTH_EDGE\n"
            "\t\t\tBackground = ftCaveBackground(Background, cameraPosition, feetPlayerPos_normalized, ftDaylight(unsigned_WsunVec.y));\n"
            "\t\t#elif defined FANTASYTRUTH_MOUTH\n"
            "\t\t\tfloat mouth = ftMouthWeight(cameraPosition, feetPlayerPos_normalized, 1680.0);\n"
            "\t\t\tBackground = mix(Background, mix(vec3(0.002, 0.004, 0.007), vec3(0.14, 0.25, 0.27), ftDaylight(unsigned_WsunVec.y)), mouth);\n"
            "\t\t#endif\n\t\t"
            "gl_FragData[0].rgb = clamp(fp10Dither(Background, triangularize(noise_2)), 0.0, 65000.);"
        ),
        "gl_FragData[0].rgb = (Indirect_lighting + Direct_lighting) * albedo;": (
            "#ifdef FANTASYTRUTH_EDGE\n"
            "\t\t\tif (!hand) Indirect_lighting = ftCaveFill(Indirect_lighting, cameraPosition + feetPlayerPos, lightmap.y, ftDaylight(unsigned_WsunVec.y));\n"
            "\t\t#elif defined FANTASYTRUTH_MOUTH\n"
            "\t\t\tif (!hand && ftMouthCamera(cameraPosition))\n"
            "\t\t\t\tIndirect_lighting = ftCaveFill(Indirect_lighting, cameraPosition + feetPlayerPos, lightmap.y, ftDaylight(unsigned_WsunVec.y));\n"
            "\t\t#endif\n\t\t"
            "gl_FragData[0].rgb = (Indirect_lighting + Direct_lighting) * albedo;"
        ),
    }
    for original, replacement in hooks.items():
        if source.count(original) != 1:
            raise ValueError("The Bliss lighting entry point has changed.")
        source = source.replace(original, replacement, 1)
    return source


def replace_once(source, original, replacement):
    if source.count(original) != 1:
        raise ValueError(f"The Bliss entry point {original!r} has changed.")
    return source.replace(original, replacement, 1)


def patch_clouds(source):
    depth = "vec3 viewPos0 = toScreenSpace_DH(tc/RENDER_SCALE , z0, DH_z0);"
    source = replace_once(source, "void main() {", f"""
    #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
        #include "{EDGE_DEPTH}"
    #endif
    void main() {{""")
    source = replace_once(source, depth, depth + """
    #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
        #ifdef FANTASYTRUTH_MOUTH
            bool useAbyssVolume = ftMouthCamera(cameraPosition);
        #else
            float shaft = ftShaft(cameraPosition);
            bool useAbyssVolume = shaft > 0.0;
        #endif
        if (useAbyssVolume && isEyeInWater == 0) {
            bool surface;
            vec3 edgePosition = ftSurfacePosition(tc / RENDER_SCALE, z0, DH_z0, surface);
            float volumeDepth = ftVolumeDepth(edgePosition, surface);
            if (!surface) edgePosition = toScreenSpaceVector(vec3(tc / RENDER_SCALE, 0.9)) * 1680.0;
            #ifdef FANTASYTRUTH_MOUTH
                vec3 worldRay = mat3(gbufferModelViewInverse) * edgePosition;
                float distanceToSurface = length(worldRay);
                float edgeWeight = ftMouthWeight(cameraPosition, worldRay / max(0.001, distanceToSurface), distanceToSurface);
            #else
                float edgeWeight = shaft;
            #endif
            if (edgeWeight > 0.0) {
                gl_FragData[0] = ftEdgeVolume(edgePosition, lightCol.rgb / 80.0, averageSkyCol / 30.0, edgeWeight, ftDaylight(sunElevation));
                gl_FragData[1] = vec4(1.0, volumeDepth, 1.0, 0.0);
                return;
            }
        }
    #endif
""")
    return source


def patch_composite(source):
    source = replace_once(source, "void main() {", f"""
    #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
        #include "{EDGE_BOUNDS}"
        #include "{EDGE_DEPTH}"
        #include "{EDGE_UPSAMPLE}"
    #endif
    void main() {{""")
    first = source.index("  #ifdef DISTANT_HORIZONS\n    vec4 vl = BilateralUpscale(")
    last = source.index("  #endif", first) + len("  #endif")
    original = source[first:last].replace("vec4 vl =", "vl =")
    source = source[:first] + """
    vec4 vl;
    #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
        #ifdef FANTASYTRUTH_MOUTH
            bool surface = false;
            vec3 position = vec3(0.0);
            float edgeWeight = 0.0;
            if (ftMouthCamera(cameraPosition) && isEyeInWater == 0) {
                vec2 uv = texcoord / RENDER_SCALE;
                position = ftSurfacePosition(uv, z, DH_depth0, surface);
                vec3 viewRay = surface ? position : normalize(toScreenSpace(vec3(uv, 0.9))) * 1680.0;
                vec3 worldRay = mat3(gbufferModelViewInverse) * viewRay;
                float distanceToSurface = length(worldRay);
                edgeWeight = ftMouthWeight(cameraPosition, worldRay / max(0.001, distanceToSurface), distanceToSurface);
            }
        #else
        float edgeWeight = isEyeInWater == 0 ? ftShaft(cameraPosition) : 0.0;
        #endif
        if (edgeWeight > 0.0) {
            #ifndef FANTASYTRUTH_MOUTH
            bool surface;
            vec3 position = ftSurfacePosition(texcoord / RENDER_SCALE, z, DH_depth0, surface);
            #endif
            vl = ftUpsampleVolume(colortex0, colortex14, texcoord, ftVolumeDepth(position, surface));
            cloudAlpha = 1.0;
        } else
    #endif
    {
""" + original + """
    }
""" + source[last:]
    source = replace_once(source, "if(swappedDepth >= 1.0 || isEyeInWater != 0) fog = 0.0;",
                          """if(swappedDepth >= 1.0 || isEyeInWater != 0) fog = 0.0;
    #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
        fog *= 1.0 - edgeWeight;
    #endif""")
    return replace_once(source, "if (isEyeInWater == 0 && eyeAltitude < 1500){", """
    if (isEyeInWater == 0 && eyeAltitude < 1500
        #if defined FANTASYTRUTH_EDGE || defined FANTASYTRUTH_MOUTH
            && edgeWeight <= 0.0
        #endif
    ) {""")


def patch_lod_terrain(source):
    source = replace_once(source, "void main() {", f"""
    #ifdef FANTASYTRUTH_EDGE
        #include "{EDGE_BOUNDS}"
    #endif
    void main() {{""")
    # Vanilla still overwrites loaded terrain. Let DH fill holes in the
    # overlapping band while near chunks are streaming into the deep shaft.
    return replace_once(source,
                        "if(clamp(1.0-length(pos.xyz)/clamp(far - 32.0,32.0,maxOverdrawDistance),0.0,1.0) > 0.0 ){",
                        """
        if (length(pos.xyz) < clamp(far - 32.0, 32.0, maxOverdrawDistance)
            #ifdef FANTASYTRUTH_EDGE
                && ftShaft(cameraPosition) <= 0.0
            #endif
        ) {""")


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
            LIGHTING: patch_lighting(text(source, LIGHTING)),
            CLOUDS: patch_clouds(text(source, CLOUDS)),
            FOG_COMPOSITE: patch_composite(text(source, FOG_COMPOSITE)),
            LOD_TERRAIN: patch_lod_terrain(text(source, LOD_TERRAIN)),
        }
        glsl = Path(__file__).with_name("shaders")
        mouth_wrappers = {"shaders/world0/composite1.fsh", "shaders/world0/composite2.fsh",
                          "shaders/world0/composite3.fsh"}
        for path in mouth_wrappers:
            edits[path] = re.sub(r"(?m)^(#version[^\n]*\n)", r"\1#define FANTASYTRUTH_MOUTH\n",
                                 wrappers[path], count=1)
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
                for include in (EDGE_INCLUDE, EDGE_VIEW, EDGE_DEPTH, EDGE_BOUNDS, EDGE_UPSAMPLE):
                    output.write(glsl / Path(include).name, f"shaders{include}")
                output.writestr(
                    "FANTASYTRUTH-EDITS.md",
                    "# FantasyTruth Abyss\n\n"
                    "Companion edit of Bliss by Xonk, based on Chocapic13's Shaders. "
                    "The original credit and license files are retained.\n\n"
                    "FantasyTruth adds the Edge of the Abyss dimension route, local cloud sheets "
                    "and shadowed light shafts, with an aperture-aware background and cave fill light. "
                    "The Edge fog pass uses both depth buffers and filters out samples from the other side of terrain edges. "
                    "Other dimensions keep the Bliss rendering programs. "
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
