vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float row = noise(vec2(3.0, fc.y / (2.0 * uDpr) * 0.12));
  float len = (0.06 + 0.12 * row) * (1.0 - m);
  vec3 acc = vec3(0.0), hi = vec3(0.0);
  for (int i = 0; i < 10; i++) {
    float k = float(i) / 9.0;
    vec3 s = cur(uv + vec2(len * k, len * k * 0.35));
    acc += s; hi = max(hi, s * (1.0 - k * 0.6));
  }
  vec3 col = mix(acc / 10.0, hi, 0.55);
  float fade = smoothstep(0.0, 0.75, m + row * 0.25 * (1.0 - m));
  return mix(mix(uBg, col, fade * border(uv)), c, smoothstep(0.85, 1.0, m));
}
