vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 gpos = abs(fract(fc / (14.0 * uDpr)) - 0.5) * 2.0;
  float w = mix(0.02, 1.0, pow(m, 1.6));
  float grid = max(smoothstep(1.0 - w - 0.06, 1.0 - w, gpos.x), smoothstep(1.0 - w - 0.06, 1.0 - w, gpos.y));
  float node = smoothstep(0.35, 0.0, length(gpos - 1.0)) * (1.0 - m);
  vec3 col = uBg + cur(uv) * (grid * (0.4 + 0.8 * m) + node * 0.9) * border(uv);
  return mix(col, c, smoothstep(0.8, 1.0, m));
}
