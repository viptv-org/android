vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float n = fbm(vec2(uv.x * 2.5, uv.y * 55.0));
  float n2 = noise(vec2(uv.x * 2.0, uv.y * 12.0));
  float th = m * 1.4 - 0.2 + (n - 0.5) * 1.3 * (1.0 - m);
  float k = smoothstep(0.44, 0.56, th);
  vec3 col = cur(uv + vec2((1.0 - m) * (0.05 * (n2 - 0.5) + 0.03), 0.0));
  col *= 0.8 + 0.35 * noise(vec2(uv.x * 6.0, fc.y / (1.5 * uDpr)));
  return mix(uBg, mix(col, c, smoothstep(0.6, 1.0, th)), k);
}
