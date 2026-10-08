vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  // These styles resolve to unchanged artwork outside the fade band.
  if (m >= 1.0 && uv.y >= 0.42) return c;
  float wob = (fbm(uv * vec2(2.5, 2.0) + uTime * 0.02) - 0.5) * 0.25 * m * (1.0 - m) * 4.0;
  float mm = clamp(m + wob, 0.0, 1.0);
  vec3 soft = mix(blurred(uv, 4.5), c, smoothstep(0.15, 0.7, mm));
  float bleed = smoothstep(0.0, 0.55, uv.x) * smoothstep(0.0, 0.5, uv.y);
  vec3 bg = uBg + blurred(uv, 9.0) * 0.22 * bleed;
  return mix(bg, soft, pow(mm, 1.1));
}
