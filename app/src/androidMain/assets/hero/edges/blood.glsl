vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float w = 9.0 * uDpr;
  float col_ = floor(fc.x / w);
  float shape = sin(PI * fract(fc.x / w));
  float dl = (0.03 + 0.25 * pow(hash1(col_), 3.0)) * (0.7 + 0.3 * sin(uTime * 0.25 + col_)) * sqrt(shape);
  float md = max(m, edgeMask(uv + vec2(0.0, dl)));
  float inside = smoothstep(0.27, 0.31, md);
  vec3 src = cur(uv + vec2(0.0, dl * 0.7));
  vec3 red = mix(src * vec3(0.5, 0.05, 0.05) + vec3(0.1, 0.0, 0.005), c, smoothstep(0.3, 0.5, m));
  float gloss = smoothstep(0.31, 0.29, md) * inside * 0.5;
  return mix(uBg, red + vec3(0.45, 0.12, 0.12) * gloss, inside);
}
