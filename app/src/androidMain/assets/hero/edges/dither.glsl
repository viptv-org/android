vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float px = 3.0 * uDpr;
  vec2 cell = floor(fc / px);
  float t = eb8(cell);
  float mc = edgeMask((cell + 0.5) * px / uRes);
  vec3 q = floor(c * 5.0 + t) / 5.0;
  vec3 col = mix(q, c, smoothstep(0.75, 1.0, mc));
  return mc > 0.985 ? c : (t + 0.015 < pow(mc, 1.15) ? col : uBg);
}
