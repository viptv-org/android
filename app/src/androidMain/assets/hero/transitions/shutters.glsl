vec4 transition(vec2 uv, float p) {
  const float N = 12.0;
  float i = floor(uv.x * N);
  float delay = (i / N) * 0.45;
  float lp = ease(clamp((p - delay) / 0.55, 0.0, 1.0));
  float yTop = 1.0 - uv.y;
  float inB = step(yTop, lp);
  vec4 a = getFrom(vec2(uv.x, uv.y + lp * 0.25));
  vec4 b = getTo(vec2(uv.x, uv.y + (1.0 - lp) * 0.15));
  float shade = smoothstep(0.08, 0.0, lp - yTop) * (1.0 - inB) * step(0.001, lp);
  a.rgb *= 1.0 - 0.6 * shade;
  return mix(a, b, inB);
}
