vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float s = 18.0 * uDpr;
  float column = floor(fc.x / s);
  vec2 g = vec2(fc.x, fc.y + uTime * (25.0 + 35.0 * hash1(column)) * uDpr) / s;
  vec2 cell = floor(g);
  vec2 f = rot2(hash(cell) * 6.28 + uTime * (hash(cell + 1.0) - 0.5) * 4.0) * (fract(g) - 0.5);
  float piece = step(abs(f.x), 0.28) * step(abs(f.y), 0.1) * step(0.55, hash(cell + 2.0));
  vec3 colr = pal(hash(cell + 3.0)) * (0.7 + 0.3 * sign(f.y));
  vec3 base = mix(uBg, c, smoothstep(0.0, 1.0, m));
  return mix(base, colr, piece * zone(uv, m) * smoothstep(0.0, 0.3, m + 0.1));
}
