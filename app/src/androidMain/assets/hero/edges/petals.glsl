vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec3 base = mix(uBg, c, smoothstep(0.0, 1.0, m)) + blurred(uv, 6.0) * 0.15 * m * (1.0 - m) * 4.0 * border(uv);
  float petals = 0.0; vec3 tint = vec3(0.0);
  for (int i = 0; i < 2; i++) {
    float fi = float(i);
    float s = (34.0 - fi * 12.0) * uDpr;
    vec2 g = (fc + vec2(uTime * (18.0 + fi * 10.0), uTime * (26.0 + fi * 12.0)) * uDpr) / s;
    vec2 cell = floor(g);
    vec2 ctr = vec2(hash(cell + fi), hash(cell + 4.0 + fi)) * 0.5 + 0.25;
    vec2 d = rot2(hash(cell) * 6.28 + uTime * (0.5 + hash(cell + 1.0))) * (fract(g) - ctr);
    float p = smoothstep(0.17, 0.12, length(d * vec2(1.0, 2.3))) * step(0.62, hash(cell + 2.0 + fi));
    petals = max(petals, p);
    tint = mix(tint, vec3(1.0, 0.72, 0.8) * (0.75 + 0.3 * hash(cell + 3.0)), p);
  }
  return mix(base, tint, petals * zone(uv, m) * smoothstep(0.0, 0.3, m + 0.1));
}
