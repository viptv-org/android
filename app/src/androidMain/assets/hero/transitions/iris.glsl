vec4 blurFrom(vec2 uv, float r) {
  vec4 acc = getFrom(uv); float w = 1.0;
  for (int i = 1; i < 14; i++) {
    float fi = float(i), a = fi * 2.39996, d = sqrt(fi / 14.0) * r;
    acc += getFrom(uv + vec2(cos(a), sin(a)) * d / aspect()); w += 1.0;
  }
  return acc / w;
}
vec4 blurTo(vec2 uv, float r) {
  vec4 acc = getTo(uv); float w = 1.0;
  for (int i = 1; i < 14; i++) {
    float fi = float(i), a = fi * 2.39996, d = sqrt(fi / 14.0) * r;
    acc += getTo(uv + vec2(cos(a), sin(a)) * d / aspect()); w += 1.0;
  }
  return acc / w;
}
vec4 transition(vec2 uv, float p) {
  float e = ease(p);
  vec2 c = uFocus;
  float d = length((uv - c) * aspect());
  float maxR = length(max(c, 1.0 - c) * aspect()) + 0.45;
  float r = e * maxR;
  float wobble = 0.03 * sin(atan(uv.y - c.y, (uv.x - c.x) * aspect().x) * 5.0 + uTime * 0.8);
  float m = smoothstep(r - 0.4, r, d + wobble);           // 1 = still showing "from"
  float rim = exp(-pow((d - r + 0.2) / 0.22, 2.0)) * sin(PI * p);
  vec2 inUv = c + (uv - c) * mix(1.12, 1.0, e);           // incoming image settles from a slight zoom
  vec4 a = blurFrom(uv, 0.002 + 0.03 * rim);
  vec4 b = blurTo(inUv, 0.002 + 0.04 * rim + 0.02 * (1.0 - e));
  vec4 col = mix(b, a, m);
  return col + vec4(vec3(0.10, 0.08, 0.04) * rim, 0.0);
}
