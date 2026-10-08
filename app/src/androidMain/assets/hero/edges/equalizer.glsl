vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float w = 10.0 * uDpr;
  float col_ = floor(fc.x / w);
  float lvl = luma(blurred(vec2((col_ + 0.5) * w / uRes.x, 0.5), 4.0));
  float h = (0.25 + 0.75 * lvl) * (0.55 + 0.45 * sin(uTime * (2.0 + 3.0 * hash1(col_)) + hash1(col_) * 6.0));
  float on = step(uv.y, h) * step(0.2, fract(fc.x / w)) * step(0.3, fract(fc.y / (5.0 * uDpr)));
  vec3 led = mix(c * 1.3, mix(vec3(0.2, 1.0, 0.4), vec3(1.0, 0.3, 0.2), uv.y / max(h, 0.01)), 0.35);
  vec3 zoneCol = uBg + led * on * zone(uv, m) * smoothstep(0.0, 0.3, m + 0.2);
  return mix(zoneCol, c, smoothstep(0.45, 0.9, m));
}
