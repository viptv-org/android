vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float n = fbm(uv * vec2(6.0, 5.0));
  float n2 = fbm(uv * 22.0);
  float k = clamp(m * 1.3 - 0.15 + (n - 0.5) * 0.5, 0.0, 1.0);
  vec3 wash = blurred(uv + (vec2(n, n2) - 0.5) * 0.025, 4.0);
  float rim = smoothstep(0.25, 0.32, k) * smoothstep(0.45, 0.32, k);
  vec3 pig = wash * (0.88 + 0.2 * n2);
  pig = mix(vec3(luma(pig)), pig, 1.0 + rim * 0.9) * (1.0 + rim * 0.25);
  vec3 col = mix(pig, c, smoothstep(0.55, 0.95, k));
  return mix(uBg, col, smoothstep(0.25, 0.32, k));
}
