vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float f1 = fbm(uv * vec2(3.0, 2.0) + vec2(uTime * 0.04, 0.0));
  float f2 = fbm(uv * vec2(6.0, 4.0) - vec2(uTime * 0.07, uTime * 0.01));
  float dens = clamp((1.0 - m) * (0.5 + 1.6 * f1 * f2), 0.0, 1.0);
  vec3 fogCol = vec3(0.42, 0.45, 0.5) * 0.55 + blurred(uv, 7.0) * 0.25;
  vec3 col = mix(c, fogCol, dens);
  return mix(uBg, col, smoothstep(0.0, 0.45, m + f1 * 0.25) * border(uv));
}
