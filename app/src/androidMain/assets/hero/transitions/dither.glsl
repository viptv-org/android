float bayer2(vec2 a) { a = floor(a); return fract(dot(a, vec2(0.5, a.y * 0.75))); }
float bayer4(vec2 a) { return bayer2(0.5 * a) * 0.25 + bayer2(a); }
float bayer8(vec2 a) { return bayer4(0.5 * a) * 0.25 + bayer2(a); }
vec3 quant(vec3 c, float t, float levels) { return floor(c * levels + t) / levels; }
vec4 transition(vec2 uv, float p) {
  float px = 3.0 * uDpr;
  vec2 cell = floor(gl_FragCoord.xy / px);
  vec2 cuv = (cell + 0.5) * px / uRes;
  float t = bayer8(cell);
  float sweep = 0.6 * (1.0 - uv.x) * 0.7 + 0.3 * fbm(uv * 2.5) + 0.1 * uv.y;
  float local = clamp(p * 1.7 - sweep, 0.0, 1.0);
  float band = sin(PI * local);
  vec4 a = getFrom(mix(uv, cuv, step(0.02, band)));
  vec4 b = getTo(mix(uv, cuv, step(0.02, band)));
  vec4 col = t < local ? b : a;
  vec3 q = quant(col.rgb, t, 2.0);
  vec3 duo = mix(vec3(0.05, 0.04, 0.03), vec3(1.0, 0.86, 0.45), step(t, luma(col.rgb) * 1.1));
  col.rgb = mix(col.rgb, mix(q, duo, smoothstep(0.55, 1.0, band)), smoothstep(0.0, 0.35, band));
  return col;
}
