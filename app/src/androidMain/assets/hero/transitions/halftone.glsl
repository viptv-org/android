vec4 transition(vec2 uv, float p) {
  float size = 11.0 * uDpr;
  float ang = 0.785398;
  mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
  mat2 inv = mat2(cos(ang), sin(ang), -sin(ang), cos(ang));
  vec2 g = rot * gl_FragCoord.xy / size;
  vec2 f = fract(g) - 0.5;
  vec2 centerPx = inv * ((floor(g) + 0.5) * size);
  vec2 cuv = centerPx / uRes;
  float d = length((cuv - uFocus) * aspect());
  float lp = clamp(p * 1.8 - d * 0.7, 0.0, 1.0);
  vec4 b = getTo(uv);
  float lb = luma(getTo(cuv).rgb);
  float r = lp * (0.45 + 0.55 * lb) * 0.95 + smoothstep(0.6, 1.0, lp) * 0.5;
  float aa = 1.5 / size;
  float dot_ = smoothstep(r + aa, r - aa, length(f));
  vec4 col = mix(getFrom(uv), b, dot_);
  return col;
}
