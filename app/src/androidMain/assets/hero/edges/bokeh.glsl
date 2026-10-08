vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float s = 30.0 * uDpr;
  vec2 g = fc / s, cell = floor(g), f = fract(g);
  vec2 ctr = vec2(hash(cell), hash(cell + 4.0)) * 0.6 + 0.2;
  float r = 0.18 + 0.22 * hash(cell + 8.0);
  vec2 cuv = (cell + ctr) * s / uRes;
  float mc = edgeMask(cuv);
  vec3 cc = blurred(cuv, 5.0);
  float dd = length(f - ctr);
  float disc = smoothstep(r, r - 0.04, dd);
  float ring = smoothstep(0.05, 0.0, abs(dd - r + 0.03));
  float bright = smoothstep(0.3, 0.75, luma(cc)) * (1.0 - mc) * smoothstep(0.0, 0.25, mc);
  bright *= 0.8 + 0.2 * sin(uTime * 0.8 + hash(cell) * 6.0);
  vec3 bg = mix(uBg, blurred(uv, 6.0), smoothstep(0.0, 0.8, m));
  return mix(bg, c, smoothstep(0.55, 1.0, m)) + cc * (disc * 0.5 + ring * 0.4) * bright * 1.4 * border(uv);
}
