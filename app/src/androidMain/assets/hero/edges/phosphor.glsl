vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float sub = mod(floor(fc.x / (1.5 * uDpr)), 3.0);
  vec3 triad = vec3(step(sub, 0.5), step(0.5, sub) * step(sub, 1.5), step(1.5, sub));
  float gap = step(0.25, fract(fc.y / (5.0 * uDpr)));
  vec3 col = c * triad * gap * 2.6 * pow(m, 0.9);
  return mix(mix(uBg, col, smoothstep(0.0, 0.2, m)), c, smoothstep(0.7, 1.0, m));
}
