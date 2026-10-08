vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 r = rot2(0.26) * fc;
  float v = abs(fract(r.y / (5.0 * uDpr)) - 0.5) * 2.0;
  float w = luma(c) * pow(m, 0.8) * 1.1;
  float on = smoothstep(w + 0.12, w - 0.12, v);
  vec3 ink = vec3(0.93, 0.91, 0.86) * on * 0.9;
  return mix(mix(uBg, ink, smoothstep(0.0, 0.1, m)), c, smoothstep(0.65, 1.0, m));
}
