vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float s = 16.0 * uDpr;
  vec2 cell = floor(fc / s);
  vec2 f = fract(fc / s) - 0.5;
  float d = hash(cell) > 0.5 ? abs(f.y) : abs(f.x);
  float trace = smoothstep(0.08, 0.03, d) * step(0.25, hash(cell + 3.0));
  float pad = smoothstep(0.2, 0.13, length(f)) * step(0.7, hash(cell + 5.0));
  vec2 cuv = (cell + 0.5) * s / uRes;
  float mc = edgeMask(cuv);
  float pulse = smoothstep(0.85, 1.0, fract(uTime * 0.4 + hash(cell + 9.0)));
  vec3 tint = mix(vec3(0.2, 1.0, 0.6), blurred(cuv, 3.0) * 1.6, 0.5);
  vec3 glow = tint * (trace + pad) * (0.3 + 0.7 * mc + pulse) * smoothstep(0.0, 0.2, mc + 0.1);
  return mix(uBg + glow * zone(uv, m), c, smoothstep(0.55, 1.0, m));
}
