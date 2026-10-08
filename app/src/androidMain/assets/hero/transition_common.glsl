precision highp float;
varying vec2 vUv;
uniform sampler2D uFrom, uTo;
uniform vec2 uRes, uFromSize, uToSize, uFromDrift, uToDrift, uFocus;
uniform float uProgress, uTime, uFromT, uToT, uDpr;

#define PI 3.14159265

vec2 kenBurns(vec2 uv, vec2 img, float t, vec2 drift) {
  float rs = uRes.x / uRes.y, ri = img.x / img.y;
  vec2 s = rs < ri ? vec2(rs / ri, 1.0) : vec2(1.0, ri / rs);
  // Starts at the full frame and zooms to at most 1.04x; the drift stays
  // within that zoom's margin so the frame edge never comes into view.
  float k = 1.0 - exp(-t / 22.0);
  float z = 1.0 + 0.04 * k;
  uv = (uv - 0.5) / z + 0.5 + drift * 0.5 * (1.0 - 1.0 / z);
  return (uv - 0.5) * s + 0.5;
}
vec2 flipV(vec2 p) { return vec2(p.x, 1.0 - p.y); }
vec4 getFrom(vec2 uv) { return texture2D(uFrom, flipV(kenBurns(uv, uFromSize, uFromT, uFromDrift))); }
vec4 getTo(vec2 uv)   { return texture2D(uTo,   flipV(kenBurns(uv, uToSize,   uToT,   uToDrift))); }
// Mip-biased samples: a cheap defocus where the art has mipmaps (OpenGL ES 3);
// without mipmaps the bias is ignored and the sample stays sharp.
vec4 getFromB(vec2 uv, float b) { return texture2D(uFrom, flipV(kenBurns(uv, uFromSize, uFromT, uFromDrift)), b); }
vec4 getToB(vec2 uv, float b)   { return texture2D(uTo,   flipV(kenBurns(uv, uToSize,   uToT,   uToDrift)), b); }

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
  float v = 0.0, a = 0.5;
  for (int i = 0; i < 4; i++) { v += a * noise(p); p = p * 2.03 + 17.1; a *= 0.5; }
  return v;
}
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec2 aspect() { return vec2(uRes.x / uRes.y, 1.0); }
// Transitions front-load their motion so the incoming art leads within the
// first third of the duration and the rest is settling.
float outCubic(float t) { return 1.0 - pow(1.0 - t, 3.0); }
float outExpo(float t) { return t >= 1.0 ? 1.0 : 1.0 - pow(2.0, -10.0 * t); }
