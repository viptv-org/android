precision highp float;
varying vec2 vUv;
uniform sampler2D uScene, uGlyphs;
uniform vec2 uRes, uFocus;
uniform float uTime, uDpr, uMorph;
uniform vec3 uGround;
uniform vec2 uView, uOrigin;
vec3 uBg;
#define PI 3.14159265

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float hash1(float x) { return fract(sin(x * 127.1) * 43758.5453); }
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
  float v = 0.0, a = 0.5;
  for (int i = 0; i < 5; i++) { v += a * noise(p); p = p * 2.03 + 17.1; a *= 0.5; }
  return v;
}
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec2 aspect() { return vec2(uRes.x / uRes.y, 1.0); }
vec3 blurred(vec2 uv, float b) { return texture2D(uScene, clamp(uv, 0.001, 0.999), b).rgb; }
vec3 cur(vec2 uv) { return texture2D(uScene, clamp(uv, 0.001, 0.999)).rgb; }
// The design's ambient layer: the art cover-fitted to the whole backdrop,
// heavily blurred, at 0.6 opacity over the page ground.
vec3 ambient(vec2 viewUv) {
  float rv = uView.x / uView.y, ra = uRes.x / uRes.y;
  vec2 s = rv < ra ? vec2(rv / ra, 1.0) : vec2(1.0, ra / rv);
  vec2 auv = (viewUv - 0.5) * s + 0.5;
  vec3 acc = blurred(auv, 6.0);
  for (int i = 0; i < 6; i++) {
    float a = float(i) * 1.0472;
    acc += blurred(auv + vec2(cos(a), sin(a)) * 0.035, 6.0);
  }
  return mix(uGround, acc / 7.0, 0.6);
}
float eb2(vec2 a) { a = floor(a); return fract(dot(a, vec2(0.5, a.y * 0.75))); }
float eb4(vec2 a) { return eb2(0.5 * a) * 0.25 + eb2(a); }
float eb8(vec2 a) { return eb4(0.5 * a) * 0.25 + eb2(a); }
float edgeMask(vec2 uv) {
  float ax = smoothstep(0.02, 0.30, uv.x), ay = smoothstep(0.02, 0.26, uv.y);
  float m = 1.0 - clamp(length(vec2(1.0 - ax, 1.0 - ay)), 0.0, 1.0);
  return m * m * (3.0 - 2.0 * m);
}
// Hard stop at the canvas border so effects that spill never show a seam.
float border(vec2 uv) { return smoothstep(0.0, 0.08, uv.x) * smoothstep(0.0, 0.06, uv.y); }
// Zone weight: strongest in the fade band, zero deep inside and at the border.
float zone(vec2 uv, float m) { return (1.0 - smoothstep(0.55, 0.95, m)) * border(uv); }
vec2 hexCenter(vec2 p) {
  vec2 r = vec2(1.0, 1.7320508), h = r * 0.5;
  vec2 a = mod(p, r) - h, b = mod(p - h, r) - h;
  return dot(a, a) < dot(b, b) ? p - a : p - b;
}
float hexDist(vec2 q) { q = abs(q); return max(dot(q, vec2(0.5, 0.8660254)), q.x); }
// Voronoi: returns (seam distance, cell point xy); cid = cell id.
vec3 voro(vec2 p, out vec2 cid) {
  vec2 ip = floor(p), fp = fract(p);
  float md = 8.0, md2 = 8.0; vec2 cpos = vec2(0.0); cid = vec2(0.0);
  for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
    vec2 g = vec2(float(x), float(y));
    vec2 o = vec2(hash(ip + g), hash(ip + g + 7.3));
    vec2 r = g + o - fp;
    float d = dot(r, r);
    if (d < md) { md2 = md; md = d; cid = ip + g; cpos = ip + g + o; }
    else if (d < md2) { md2 = d; }
  }
  return vec3(sqrt(md2) - sqrt(md), cpos);
}
mat2 rot2(float a) { return mat2(cos(a), -sin(a), sin(a), cos(a)); }
vec3 pal(float t) { return 0.5 + 0.5 * cos(6.2832 * (t + vec3(0.0, 0.33, 0.67))); }
