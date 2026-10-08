vec4 transition(vec2 uv, float p) {
  float s = sin(PI * p);
  float e = ease(p);
  vec2 c = uFocus;
  vec2 au = c + (uv - c) / (1.0 + e * 0.6);
  vec2 bu = c + (uv - c) / mix(0.75, 1.0, e);
  vec4 a = vec4(0.0), b = vec4(0.0);
  for (int i = 0; i < 16; i++) {
    float k = float(i) / 15.0 * 0.12 * s;
    a += getFrom(c + (au - c) * (1.0 - k));
    b += getTo(c + (bu - c) * (1.0 - k));
  }
  return mix(a / 16.0, b / 16.0, smoothstep(0.35, 0.65, p));
}
