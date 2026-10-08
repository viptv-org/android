vec4 transition(vec2 uv, float p) {
  float s = sin(PI * p);
  float tt = floor(uTime * 18.0);
  float row = floor(uv.y * 26.0);
  float blk = hash(vec2(row, tt));
  float shift = (blk > 0.62 ? (hash(vec2(tt, row)) - 0.5) * 0.25 : 0.0) * s;
  vec2 u = vec2(uv.x + shift, uv.y);
  float pick = step(hash(vec2(floor(uv.x * 9.0), row) + tt * 0.1), p * 1.2 - 0.1);
  float ca = 0.012 * s;
  vec3 a = vec3(getFrom(u + vec2(ca, 0)).r, getFrom(u).g, getFrom(u - vec2(ca, 0)).b);
  vec3 b = vec3(getTo(u + vec2(ca, 0)).r, getTo(u).g, getTo(u - vec2(ca, 0)).b);
  vec3 col = mix(a, b, mix(pick, smoothstep(0.3, 0.7, p), 0.4));
  col *= 1.0 - 0.18 * s * step(0.5, fract(gl_FragCoord.y / (3.0 * uDpr)));
  return vec4(col, 1.0);
}
