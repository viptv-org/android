vec4 transition(vec2 uv, float p) {
  vec2 q = uv * aspect();
  float n = fbm(q * 2.6 + vec2(uTime * 0.04, -uTime * 0.03));
  float d = length((uv - uFocus) * aspect());
  float field = n * 0.65 + (1.0 - clamp(d, 0.0, 1.2) / 1.2) * 0.35;
  float th = 0.92 - ease(p) * 1.05;
  float m = smoothstep(th - 0.025, th + 0.025, field);
  float edge = exp(-pow((field - th) / 0.03, 2.0)) * sin(PI * p);
  vec2 warp = (vec2(noise(q * 6.0), noise(q * 6.0 + 9.0)) - 0.5) * 0.03 * edge;
  vec4 col = mix(getFrom(uv + warp), getTo(uv - warp), m);
  return col + vec4(vec3(1.0, 0.62, 0.22) * edge * 0.9, 0.0);
}
