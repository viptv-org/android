vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 jit = vec2((hash1(floor(uv.y * 60.0) + floor(uTime * 8.0)) - 0.5) * 0.012 * (1.0 - m), 0.0);
  float g = luma(cur(uv + jit));
  vec3 holo = vec3(0.25, 0.9, 1.0) * g * 1.5;
  holo *= 0.65 + 0.35 * sin(fc.y * PI / (2.0 * uDpr));
  float band = smoothstep(0.08, 0.0, abs(fract(uv.y * 1.5 - uTime * 0.2) - 0.5));
  holo += vec3(0.3, 1.0, 1.0) * band * 0.25 * g;
  holo *= 0.9 + 0.1 * hash1(floor(uTime * 20.0));
  vec3 col = mix(holo, c, smoothstep(0.6, 1.0, m));
  return mix(uBg, col, smoothstep(0.0, 0.6, m));
}
