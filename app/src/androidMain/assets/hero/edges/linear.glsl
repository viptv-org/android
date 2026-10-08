vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  return mix(uBg, c, clamp(uv.x / 0.34, 0.0, 1.0) * clamp(uv.y / 0.38, 0.0, 1.0));
}
