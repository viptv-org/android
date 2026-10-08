vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float f = m + (fbm(uv * 3.0 + uTime * 0.01) - 0.5) * 0.35 * (1.0 - m);
  float line = smoothstep(0.1, 0.02, abs(fract(f * 16.0 - uTime * 0.05) - 0.5));
  vec3 col = uBg + cur(uv) * (line * (0.35 + 0.9 * f) + 0.3 * f * f) * border(uv);
  return mix(col, c, smoothstep(0.78, 0.98, f));
}
