vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float size = 20.0 * uDpr;
  vec2 hp = fc / size;
  vec2 hc = hexCenter(hp);
  vec2 cuv = hc * size / uRes;
  float mc = edgeMask(cuv);
  float scale = 0.5 * smoothstep(0.02, 0.85, mc + (hash(hc) - 0.5) * 0.15);
  float tile = smoothstep(scale + 0.02, scale - 0.02, hexDist(hp - hc));
  vec3 col = mix(blurred(cuv, 3.0), c, smoothstep(0.4, 0.95, mc)) * mix(0.6, 1.0, mc);
  return mix(mix(uBg, col, tile), c, smoothstep(0.93, 1.0, m));
}
