vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float s = 14.0 * uDpr;
  vec2 g = fc / s, cell = floor(g);
  vec2 d = fract(g) - (vec2(hash(cell), hash(cell + 4.0)) * 0.6 + 0.2);
  float h = hash(cell + 7.0);
  float tw = pow(0.5 + 0.5 * sin(uTime * (1.2 + 2.0 * h) + h * 20.0), 10.0);
  float star = exp(-length(d) * 30.0) + (exp(-abs(d.x) * 70.0 - abs(d.y) * 9.0) + exp(-abs(d.y) * 70.0 - abs(d.x) * 9.0)) * 0.5;
  vec3 tint = mix(vec3(1.0, 0.9, 0.6), blurred(cell * s / uRes, 3.0) * 1.6, 0.4);
  float dens = step(0.72, h) * zone(uv, m) * smoothstep(0.0, 0.3, m + 0.08);
  vec3 base = mix(uBg + blurred(uv, 7.0) * 0.15 * border(uv) * (1.0 - m), c, smoothstep(0.0, 1.0, pow(m, 1.1)));
  return base + tint * star * tw * dens * 1.3;
}
