vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float n = fbm(uv * vec2(4.0, 3.0) + vec2(uTime * 0.05, -uTime * 0.035));
  float n2 = fbm(uv * vec2(9.0, 7.0) - vec2(uTime * 0.03, 0.0));
  float k = smoothstep(0.0, 1.0, m * 1.5 - 0.15 + (n - 0.5) * 1.6 * (1.0 - m) + (n2 - 0.5) * 0.5 * (1.0 - m));
  vec3 haze = blurred(uv, 7.0) * 0.18 * smoothstep(0.0, 0.4, uv.x) * smoothstep(0.0, 0.35, uv.y) * n;
  return mix(uBg + haze, mix(blurred(uv, 3.0), c, smoothstep(0.5, 0.9, k)), k);
}
