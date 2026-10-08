vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 cellPx = vec2(7.0, 12.0) * uDpr;
  vec2 cell = floor(fc / cellPx);
  vec2 inCell = fract(fc / cellPx);
  vec2 cuv = (cell + 0.5) * cellPx / uRes;
  float mc = edgeMask(cuv);
  vec3 cc = blurred(cuv, 2.0);
  float l = luma(cc) * pow(mc, 0.8);
  float flick = (hash(cell + floor(uTime * 3.0)) - 0.5) * 1.2 * (1.0 - mc);
  float idx = clamp(floor(l * 11.99 + flick), 0.0, 11.0);
  float g = texture2D(uGlyphs, vec2((idx + inCell.x) / 12.0, inCell.y)).r;
  return mix(uBg + cc * 1.5 * g * mc, c, smoothstep(0.7, 0.97, m));
}
