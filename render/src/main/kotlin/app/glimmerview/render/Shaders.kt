package app.glimmerview.render

internal object Shaders {

    /** Triangle plein écran généré à partir de gl_VertexID (aucun tampon de sommets). */
    const val VERTEX = """#version 300 es
uniform vec2 uUvScale;   // recadrage « cover » et zoom
uniform vec2 uUvOffset;  // défilement des pages du launcher
out vec2 vUv;

void main() {
    vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
    // y inversé : la ligne 0 des textures correspond au haut de l'image
    vec2 uv = vec2(pos.x * 0.5 + 0.5, 0.5 - pos.y * 0.5);
    vUv = (uv - 0.5) * uUvScale + 0.5 + uUvOffset;
    gl_Position = vec4(pos, 0.0, 1.0);
}
"""

    /**
     * Parallaxe par ray-marching sur la carte de disparité (parallax occlusion mapping) :
     * on descend du plan le plus proche vers le fond, la première surface rencontrée est
     * celle qui est visible, ce qui gère les occlusions. uSteps = 0 → simple décalage.
     */
    const val FRAGMENT = """#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uColor;
uniform sampler2D uDepth;   // disparité : 0 = loin, 1 = proche
uniform vec2 uShift;        // décalage max en UV (inclinaison × intensité)
uniform float uFocus;       // disparité du plan immobile
uniform int uSteps;
uniform int uMode;          // 0 = image, 1 = profondeur

in vec2 vUv;
out vec4 fragColor;

vec2 sampleUv(float h) { return vUv - uShift * (h - uFocus); }

float depthAt(vec2 uv) { return textureLod(uDepth, uv, 0.0).r; }

void main() {
    vec2 uv;
    if (uSteps <= 0) {
        uv = sampleUv(depthAt(vUv));
    } else {
        float stepH = 1.0 / float(uSteps);
        float h = 1.0;
        float d = depthAt(sampleUv(h));
        float prevH = h;
        float prevD = d;
        for (int i = 0; i < 64; i++) {
            if (i >= uSteps || d >= h) break;
            prevH = h;
            prevD = d;
            h -= stepH;
            d = depthAt(sampleUv(h));
        }
        // Affinage : interpolation linéaire entre les deux derniers pas
        float a = d - h;
        float b = prevD - prevH;
        float hHit = mix(h, prevH, clamp(a / (a - b + 1e-5), 0.0, 1.0));
        uv = sampleUv(hHit);
    }
    if (uMode == 1) {
        // Vision « cyanotype » de la charte : bleu de Prusse au loin, papier au premier plan
        float t = depthAt(uv);
        vec3 far = vec3(0.078, 0.141, 0.231);
        vec3 mid = vec3(0.239, 0.353, 0.502);
        vec3 near = vec3(0.929, 0.918, 0.886);
        fragColor = vec4(t < 0.5 ? mix(far, mid, t * 2.0) : mix(mid, near, (t - 0.5) * 2.0), 1.0);
    } else {
        fragColor = vec4(textureLod(uColor, uv, 0.0).rgb, 1.0);
    }
}
"""
}
