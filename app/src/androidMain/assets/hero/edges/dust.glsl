vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec3 sep = vec3(luma(c)) * vec3(1.1, 0.88, 0.62);
  vec3 col = mix(sep, c, smoothstep(0.5, 1.0, m));
  float grain = step(0.82, noise(vec2((fc.x + uTime * 140.0 * uDpr) / (3.0 * uDpr) * 0.25, fc.y / (1.5 * uDpr))));
  float haze = fbm(uv * 3.0 + vec2(uTime * 0.12, 0.0));
  col = mix(uBg, col, smoothstep(0.0, 0.9, m + (haze - 0.5) * 0.3 * (1.0 - m)));
  col += vec3(0.6, 0.42, 0.25) * 0.3 * haze * zone(uv, m);
  return col + vec3(0.95, 0.75, 0.5) * grain * zone(uv, m) * 0.5;
}
