// Liquid: a refractive ripple carries the incoming art through and calms
// before the transition ends.
vec4 transition(vec2 uv, float p) {
  vec2 q = uv * aspect() * 1.4;
  float t = uTime * 0.1;
  vec2 w1 = vec2(fbm(q + t), fbm(q + vec2(5.2, 1.3) - t));
  vec2 w2 = vec2(fbm(q + 3.0 * w1 + vec2(1.7, 9.2)), fbm(q + 3.0 * w1 + vec2(8.3, 2.8)));
  vec2 disp = (w2 - 0.5) * 0.22 * sin(PI * p) * (1.0 - p);
  float th = smoothstep(0.0, 1.0, outCubic(p) * 1.5 - 0.25 + (w2.x - 0.5) * 0.6);
  return mix(getFrom(uv + disp), getTo(uv - disp * 1.4), th);
}
