vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float f = m + 0.1 * sin(uv.y * 6.0 + uTime * 0.4) + 0.06 * sin(uv.x * 9.0 - uTime * 0.3);
  float r1 = exp(-pow((f - 0.2) / 0.1, 2.0));
  float r2 = exp(-pow((f - 0.38) / 0.08, 2.0));
  float curtain = pow(noise(vec2((uv.x - uv.y) * 70.0, uv.y * 2.0 - uTime * 0.25)), 2.0) * 1.6;
  float drift = 0.6 + 0.4 * fbm(uv * vec2(2.0, 3.0) + uTime * 0.05);
  vec3 col = mix(uBg, c, smoothstep(0.3, 0.9, m));
  return col + (r1 * vec3(0.15, 0.95, 0.5) + r2 * vec3(0.55, 0.25, 0.95)) * curtain * drift * 0.4 * border(uv);
}
