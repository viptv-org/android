vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 p = (uv - uFocus) * aspect();
  float a = atan(p.y, p.x);
  float ln = smoothstep(0.6, 0.8, noise(vec2(a * 70.0, floor(uTime * 8.0) * 3.1)));
  vec3 post = floor(c * 4.0 + 0.5) / 4.0;
  vec3 col = mix(post, c, smoothstep(0.6, 1.0, m));
  float base = smoothstep(0.0, 0.55, m);
  float vis = clamp(base + ln * (1.0 - base) * smoothstep(0.0, 0.25, m + 0.1), 0.0, 1.0);
  vec3 lineCol = blurred(uv, 2.0) * 1.3 + 0.12;
  return mix(uBg, mix(col, lineCol, ln * (1.0 - base)), vis * border(uv));
}
