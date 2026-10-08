vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float l = luma(blurred(uv, 1.0)) * pow(m, 0.7);
  float s = 6.0 * uDpr;
  float h1 = smoothstep(0.35, 0.15, abs(fract((fc.x + fc.y) / s) - 0.5) * 2.0) * step(0.12, l);
  float h2 = smoothstep(0.35, 0.15, abs(fract((fc.x - fc.y) / s) - 0.5) * 2.0) * step(0.35, l);
  float h3 = smoothstep(0.35, 0.15, abs(fract(fc.y / s) - 0.5) * 2.0) * step(0.6, l);
  float ink = max(h1, max(h2, h3));
  vec3 inkCol = mix(vec3(0.9, 0.87, 0.8), blurred(uv, 2.0) * 1.5, 0.5) * ink * (0.4 + 0.6 * m);
  return mix(mix(uBg, uBg + inkCol, smoothstep(0.0, 0.1, m)), c, smoothstep(0.6, 1.0, m));
}
