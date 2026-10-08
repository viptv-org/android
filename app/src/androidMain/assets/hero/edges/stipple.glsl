vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  // These styles resolve to unchanged artwork outside the fade band.
  if (m >= 1.0 && uv.y >= 0.42) return c;
  vec2 cell = floor(fc / (2.0 * uDpr));
  float t = fract(hash(cell) + uTime * 0.04 * hash(cell + 2.0));
  float mc = edgeMask((cell + 0.5) * 2.0 * uDpr / uRes);
  float on = step(t, pow(mc, 1.6));
  return mix(mix(uBg, c * (0.75 + 0.5 * hash(cell + 5.0)), on), c, smoothstep(0.9, 1.0, m));
}
