vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 cellPx = vec2(8.0, 13.0) * uDpr;
  vec2 cell = floor(fc / cellPx);
  vec2 inCell = fract(fc / cellPx);
  vec2 cuv = (cell + 0.5) * cellPx / uRes;
  float mc = edgeMask(cuv);
  float speed = 0.25 + hash1(cell.x) * 0.5;
  float head = fract(uTime * speed * 0.12 + hash1(cell.x + 4.0));
  float trail = exp(-fract(cuv.y - (1.0 - head)) * 9.0);
  float idx = floor(hash(cell + floor(uTime * (4.0 + 8.0 * hash1(cell.x)))) * 12.0);
  float g = texture2D(uGlyphs, vec2((idx + inCell.x) / 12.0, inCell.y)).r;
  vec3 cc = blurred(cuv, 2.0);
  float glow = mc * 0.7 + trail * (0.25 + 0.9 * (1.0 - mc)) * smoothstep(0.0, 0.25, mc + 0.12);
  vec3 col = uBg + (cc * 1.4 + vec3(0.1, 0.25, 0.1) * trail) * g * glow * border(uv);
  return mix(col, c, smoothstep(0.75, 0.97, m));
}
