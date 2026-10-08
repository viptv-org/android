// Light sweep: a soft diagonal front crosses from the right edge toward the
// copy, carrying a faint sheen; the incoming art drifts in behind it.
vec4 transition(vec2 uv, float p) {
  float e = outCubic(p);
  float s = (1.0 - uv.x) * 0.82 + (uv.y - 0.5) * 0.25;
  float front = mix(-0.35, 1.3, e);
  float k = smoothstep(front - 0.28, front, s);
  vec4 a = getFrom(uv);
  vec4 b = getTo(uv - vec2(0.035 * (1.0 - e), 0.0));
  float sheen = exp(-pow((s - front + 0.1) / 0.07, 2.0)) * sin(PI * p);
  return mix(b, a, k) + vec4(vec3(1.0, 0.96, 0.9) * sheen * 0.08, 0.0);
}
