vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float size = 8.0 * uDpr;
  vec2 g = rot2(0.785) * fc / size;
  float mc = edgeMask(rot2(-0.785) * ((floor(g) + 0.5) * size) / uRes);
  float r = 0.74 * sqrt(mc);
  float d = smoothstep(r + 0.08, r - 0.08, length(fract(g) - 0.5));
  return mix(mix(uBg, c, d), c, smoothstep(0.85, 1.0, m));
}
