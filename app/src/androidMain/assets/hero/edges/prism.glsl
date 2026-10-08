vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 off = vec2(0.045, 0.03) * (1.0 - m);
  vec3 masks = pow(vec3(edgeMask(uv - vec2(0.04, 0.02)), m, edgeMask(uv + vec2(0.04, 0.02))), vec3(1.2));
  vec3 col = vec3(cur(uv + off).r, c.g, cur(uv - off).b);
  col = mix(uBg, col, masks) + pal(m * 1.4 + uTime * 0.04) * m * (1.0 - m) * 0.35 * border(uv);
  return mix(col, c, smoothstep(0.9, 1.0, m));
}
