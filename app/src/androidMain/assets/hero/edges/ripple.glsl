vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float d = 1.0 - m;
  float wave = sin(d * 38.0 - uTime * 1.8) * m * d * 4.0;
  vec3 col = cur(uv + normalize(vec2(1.0, 0.7)) * wave * 0.012);
  col += vec3(0.75, 0.85, 1.0) * pow(max(wave, 0.0), 3.0) * 0.2;
  return mix(uBg, col, smoothstep(0.0, 1.0, clamp(m * 1.15 + wave * 0.08, 0.0, 1.0)));
}
