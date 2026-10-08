vec4 transition(vec2 uv, float p) {
  float s = sin(PI * p);
  float col = floor(gl_FragCoord.x / (2.0 * uDpr));
  float len = hash1(col) * 0.5 + 0.15 * noise(vec2(col * 0.05, 0.0));
  vec2 su = vec2(uv.x, uv.y + len * s);
  vec4 a = getFrom(uv), b = getTo(uv);
  vec4 as = getFrom(su), bs = getTo(su);
  float k = smoothstep(0.35, 0.65, p);
  vec4 base = mix(a, b, k);
  vec4 streak = mix(as, bs, k);
  float bright = smoothstep(0.35, 0.7, luma(streak.rgb));
  return mix(base, streak, bright * s);
}
