vec4 transition(vec2 uv, float p) {
  vec2 q = uv * aspect() * 1.6;
  float t = uTime * 0.12;
  vec2 w1 = vec2(fbm(q + t), fbm(q + vec2(5.2, 1.3) - t));
  vec2 w2 = vec2(fbm(q + 3.0 * w1 + vec2(1.7, 9.2)), fbm(q + 3.0 * w1 + vec2(8.3, 2.8)));
  vec2 disp = (w2 - 0.5) * 0.35 * sin(PI * p);
  float th = smoothstep(0.0, 1.0, (p * 1.4 - 0.2) + (w2.x - 0.5) * 0.6);
  vec4 a = getFrom(uv + disp * (0.6 + p));
  vec4 b = getTo(uv - disp * (1.6 - p));
  return mix(a, b, th);
}
