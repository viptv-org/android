# TV hero backdrop shaders

GLSL ES 1.00 fragment sources for the TV hero backdrop specified by
[TV-042](../../docs/platforms/TV_POLISH.md#tv-042--shader-hero-backdrop), consumed
by Android TV (OpenGL ES 2/3) only. Copied byte-exactly from `viptv-org/android`
`app/src/androidMain/assets/hero/` at
`683904d63fc86eb0482bfc10892cb93dda2a374d`, except `index.json`, which carries
only the `transitions` and `edges` catalogs. The genre-to-edge pools are product
policy owned by shared Core (`hero_edge_pool`), not by this asset or by clients.
The sources were written for VIPTV in that commit; no third-party shader source
is recorded.

## Catalog

`index.json` lists every program a client may load:

- `transitions[]`: `{id, name, duration}`. `duration` is in seconds. `fade`
  (Crossfade) is the baseline and is not drawn into the rotation.
- `edges[]`: `{id, name}`. `linear` is the baseline straight ramp and is not
  drawn into the rotation.

Every id has exactly one file, `transitions/<id>.glsl` or `edges/<id>.glsl`.
`scripts/validate.py` enforces this and rejects any other top-level key.

## Program assembly

Concatenate with a newline between parts. The vertex shader and transition main
are not files:

```glsl
// vertex shader (every program); draw a TRIANGLE_STRIP of (-1,-1) (1,-1) (-1,1) (1,1)
attribute vec2 aPos;
varying vec2 vUv;
void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
```

| Program | Fragment source |
| --- | --- |
| Transition `<id>` | `transition_common.glsl` + `transitions/<id>.glsl` + `void main() { gl_FragColor = vec4(transition(vUv, uProgress).rgb, 1.0); }` |
| Edge `<id>` | `edge_common.glsl` + `edges/<id>.glsl` + `edge_main.glsl` |
| Ambient | `edge_common.glsl` + `ambient_main.glsl` |

Each transition file defines `vec4 transition(vec2 uv, float p)`; each edge
file defines `vec3 edge(vec3 c, vec2 uv, float m, vec2 fc)`.

## Frame passes

1. Transition (or, at rest, `fade` at progress 1) into an art-sized scene
   texture through a framebuffer.
2. Ambient over the whole backdrop viewport.
3. Edge over the art rectangle (top-right of the backdrop). While the edge
   style changes, draw the outgoing style, then the incoming one with
   `uMorph` in 0–1 and `SRC_ALPHA, ONE_MINUS_SRC_ALPHA` blending.

## Uniforms

All sizes are in output pixels; coordinates follow GL (origin bottom-left).

| Uniform | Programs | Value |
| --- | --- | --- |
| `uFrom`, `uTo` | transition | Outgoing and incoming art, uploaded with the image's top row first (the shaders flip v) |
| `uFromSize`, `uToSize` | transition | Decoded art size; art is cover-fitted into `uRes` |
| `uFromDrift`, `uToDrift` | transition | Unit vector at a random angle chosen when each art is uploaded |
| `uFromT`, `uToT` | transition | Seconds since each art was uploaded (drift clock) |
| `uProgress` | transition | 0–1 over the transition's `duration`; 1 at rest |
| `uScene` | edge, ambient | The scene texture from pass 1 |
| `uGlyphs` | all | Glyph strip, below |
| `uRes` | all | Art rectangle size (1280×720 logical) |
| `uView` | edge, ambient | Backdrop size (1920×950 logical) |
| `uOrigin` | edge, ambient | Art rectangle origin within the backdrop: `(uView.x - uRes.x, uView.y - uRes.y)` |
| `uFocus` | all | `(0.62, 0.55)`, the art-relative point effects radiate from |
| `uTime` | all | Seconds since the renderer started |
| `uDpr` | all | Output pixels per logical pixel (`uRes.x / 1280`) |
| `uMorph` | edge, ambient | `-1` when the edge is not changing, otherwise 0–1 |
| `uGround` | edge, ambient | Page ground RGB, 0–1 |

The glyph strip is 12 cells of 28×48 px, characters `` .`:-=+*%#&@`` (the first
is a space), white bold monospace at 0.82 of the cell height on black, drawn
upside down to match GL's v axis.

## Blur and mipmaps

`blurred()` and the ambient fill sample the scene with a mip LOD bias (up to
6.0). That blur exists only when the scene texture has mipmaps. OpenGL ES 3
allows mipmaps on the non-power-of-two 1280×720 scene; OpenGL ES 2 does not. A
GLES 2 renderer must use a power-of-two scene texture with
generated mipmaps, or an equivalent blur pass, so the ambient fill matches the
heavy blur of the specification.
