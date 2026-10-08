vec4 transition(vec2 uv, float p) {
  float s = mix(1.0, 48.0, pow(sin(PI * p), 1.6)) * uDpr;
  vec2 cells = uRes / s;
  vec2 c = floor(uv * cells);
  vec2 quv = s > 1.5 ? (c + 0.5) / cells : uv;
  float flip = smoothstep(0.0, 1.0, (p - hash(c) * 0.35) / 0.65);
  return mix(getFrom(quv), getTo(quv), flip);
}
