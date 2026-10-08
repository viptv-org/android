vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  // These styles resolve to unchanged artwork outside the fade band.
  if (m >= 1.0 && uv.y >= 0.42) return c;
  float s = 22.0 * uDpr;
  float column = floor(fc.x / s);
  vec2 g = vec2(fc.x, fc.y - uTime * (20.0 + 30.0 * hash1(column)) * uDpr) / s;
  vec2 cell = floor(g);
  vec2 ctr = vec2(0.5 + 0.3 * sin(uTime + hash(cell) * 6.0), hash(cell + 2.0) * 0.6 + 0.2);
  float d = length(fract(g) - ctr);
  float spark = exp(-d * d * 120.0) * step(0.6, hash(cell + 5.0)) * (0.6 + 0.4 * sin(uTime * 6.0 + hash(cell) * 20.0));
  vec3 warm = mix(c * vec3(1.05, 0.95, 0.85), c, smoothstep(0.55, 1.0, m));
  vec3 base = mix(uBg, warm, smoothstep(0.05, 0.95, m));
  base += vec3(1.0, 0.35, 0.05) * 0.05 * m * (1.0 - m) * 4.0 * border(uv);
  return base + vec3(1.0, 0.55, 0.15) * spark * zone(uv, m) * smoothstep(0.0, 0.3, m + 0.1) * 1.5;
}
