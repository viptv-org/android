// Rack focus: the outgoing art pushes forward out of focus while the incoming
// art resolves from a slight zoom.
vec4 transition(vec2 uv, float p) {
  float e = outExpo(p);
  vec2 c = uFocus;
  vec4 a = getFromB(c + (uv - c) / (1.0 + 0.05 * e), 5.0 * smoothstep(0.0, 0.5, p));
  vec4 b = getToB(c + (uv - c) / mix(1.05, 1.0, e), 5.0 * (1.0 - e));
  return mix(a, b, smoothstep(0.0, 0.45, p));
}
