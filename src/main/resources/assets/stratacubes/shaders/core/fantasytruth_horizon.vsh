#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Clip;
uniform vec4 Tile;

out vec3 position;
out vec3 tilePoint;
out vec3 terrainColor;
flat out int material;
flat out int seam;

void main() {
    int flags = int(round(Color.a * 255.0));
    material = flags / 16;
    seam = flags % 16;
    tilePoint = Position;
    terrainColor = Color.rgb;
    position = Position + vec3(Tile.x, Clip.x, Tile.y);
    if (material != 0) {
        vec2 ray = abs(position.xz);
        float edge = max(ray.x, ray.y);
        float step = Tile.z * (Tile.z <= 256.0 ? 0.0625 : 0.125);
        // Let the vanilla blocks own the silhouette throughout the overlap.
        float lower = 1.0 - smoothstep(max(0.0, Clip.y - 42.0), Clip.y + 18.0, edge);
        position.y -= lower * clamp(step * 0.65, 2.0, 12.0);
    }
    gl_Position = ProjMat * (ModelViewMat * vec4(position, 1.0));
}
