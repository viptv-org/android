const float GLYPHS = 12.0;
vec4 transition(vec2 uv, float p) {
  vec2 cellPx = vec2(7.0, 12.0) * uDpr;
  vec2 cell = floor(gl_FragCoord.xy / cellPx);
  vec2 inCell = fract(gl_FragCoord.xy / cellPx);
  vec2 cuv = (cell + 0.5) * cellPx / uRes;
  float sweep = 0.55 * uv.x + 0.25 * (1.0 - uv.y) + 0.2 * hash(cell);
  float lp = clamp(p * 1.8 - sweep * 0.8, 0.0, 1.0);
  float amt = smoothstep(0.0, 0.3, lp) * smoothstep(1.0, 0.7, lp);
  bool second = lp > 0.5;
  vec4 src = second ? getTo(uv) : getFrom(uv);
  vec4 cc = second ? getTo(cuv) : getFrom(cuv);
  float l = luma(cc.rgb);
  float jitter = (hash(cell + floor(uTime * 14.0)) - 0.5) * 5.0 * smoothstep(0.35, 0.5, 1.0 - abs(lp - 0.5));
  float idx = clamp(floor(l * (GLYPHS - 0.01) + jitter), 0.0, GLYPHS - 1.0);
  float g = texture2D(uGlyphs, vec2((idx + inCell.x) / GLYPHS, inCell.y)).r;
  vec3 tint = mix(cc.rgb * 1.6 + 0.05, vec3(0.55, 1.0, 0.65) * (0.4 + l), 0.25 * smoothstep(0.4, 0.5, 1.0 - abs(lp - 0.5)));
  vec3 asciiCol = tint * g;
  return vec4(mix(src.rgb, asciiCol, amt), 1.0);
}
