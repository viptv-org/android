// Parallax push: both layers travel left, the incoming one farther, so it
// glides in and settles while the outgoing art softens away.
vec4 transition(vec2 uv, float p) {
  float e = outExpo(p);
  vec4 a = getFromB(uv + vec2(0.035 * e, 0.0), 2.5 * e);
  vec4 b = getTo(uv - vec2(0.09 * (1.0 - e), 0.0));
  return mix(a, b, smoothstep(0.0, 0.4, p));
}
