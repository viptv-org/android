vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec3 bw = vec3(luma(c)) * vec3(1.0, 0.97, 0.92) * 1.15;
  float stripe = fract((fc.x * 0.35 + fc.y) / (22.0 * uDpr));
  float lit = smoothstep(m + 0.04, m - 0.04, stripe);
  vec3 col = mix(bw, c, smoothstep(0.7, 1.0, m));
  return mix(uBg, col, lit * smoothstep(0.0, 0.15, m));
}
