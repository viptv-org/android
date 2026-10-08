vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  // These styles resolve to unchanged artwork outside the fade band.
  if (m >= 1.0 && uv.y >= 0.42) return c;
  vec3 sep = vec3(luma(c)) * vec3(1.07, 0.92, 0.72);
  float fr = floor(uTime * 18.0);
  float grain = (hash(floor(fc / uDpr) + fract(fr * 0.137) * vec2(91.0, 37.0)) - 0.5) * 0.22;
  float sx = hash1(floor(uTime * 6.0));
  float scratch = smoothstep(0.0025, 0.0, abs(uv.x - sx * 0.5)) * step(0.4, hash1(floor(uTime * 6.0) + 3.0));
  float flick = 0.97 + 0.03 * hash1(fr);
  vec3 film = (sep + grain) * flick + scratch * 0.35 * (1.0 - m);
  vec3 col = mix(film, c, smoothstep(0.55, 1.0, m));
  return mix(uBg, col, smoothstep(0.0, 1.0, pow(m, 0.9)));
}
