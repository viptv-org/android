// Bloom: the incoming art opens in a soft circle from the subject point and
// settles from a slight zoom; the opening edge stays out of focus.
vec4 transition(vec2 uv, float p) {
  float e = outCubic(p);
  vec2 c = uFocus;
  float d = length((uv - c) * aspect());
  float r = e * 1.75;
  float k = smoothstep(r - 0.5, r, d);
  vec4 a = getFromB(uv, 2.0 * (1.0 - k));
  vec4 b = getToB(c + (uv - c) / mix(1.07, 1.0, e), 3.0 * k);
  return mix(b, a, k);
}
