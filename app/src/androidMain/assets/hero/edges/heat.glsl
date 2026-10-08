vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  // These styles resolve to unchanged artwork outside the fade band.
  if (m >= 1.0 && uv.y >= 0.42) return c;
  vec2 d = vec2(noise(vec2(uv.x * 20.0, uv.y * 30.0 - uTime * 1.5)), noise(vec2(uv.x * 25.0 + 5.0, uv.y * 28.0 - uTime * 1.2))) - 0.5;
  vec3 col = cur(uv + d * (1.0 - m) * 0.03) * mix(vec3(1.15, 0.95, 0.75), vec3(1.0), m);
  vec3 bg = uBg + vec3(0.08, 0.03, 0.0) * (1.0 - m) * m * 3.0 * border(uv);
  return mix(bg, col, smoothstep(0.0, 0.9, m + d.x * 0.3 * (1.0 - m)));
}
