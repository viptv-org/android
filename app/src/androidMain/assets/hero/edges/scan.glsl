vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float line = abs(fract(fc.y / (4.0 * uDpr)) - 0.5) * 2.0;
  float on = smoothstep(m + 0.15, m - 0.15, line);
  return mix(mix(uBg, c * (0.55 + 0.45 * m), on), c, smoothstep(0.85, 1.0, m));
}
