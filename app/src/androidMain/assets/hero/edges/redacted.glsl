vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float rh = 14.0 * uDpr;
  float row = floor(fc.y / rh);
  float gap = step(0.18, fract(fc.y / rh));
  float th = hash1(row) * 0.55 + 0.2;
  float img = step(th, m);
  float bar = step(th - 0.22 - 0.1 * hash1(row + 7.0), m) * (1.0 - img) * gap;
  vec3 barCol = vec3(0.075, 0.075, 0.08) + c * 0.04;
  vec3 col = mix(uBg, barCol, bar);
  return mix(col, c * mix(0.7, 1.0, smoothstep(0.3, 0.9, m)), img);
}
