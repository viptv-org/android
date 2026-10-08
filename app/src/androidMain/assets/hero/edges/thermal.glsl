vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float t = luma(blurred(uv, 2.0));
  vec3 iron = vec3(smoothstep(0.15, 0.55, t), smoothstep(0.45, 0.9, t),
    smoothstep(0.0, 0.3, t) * (1.0 - smoothstep(0.3, 0.6, t)) + smoothstep(0.85, 1.0, t));
  iron *= 0.9 + 0.1 * hash(floor(fc / uDpr) + floor(uTime * 12.0));
  vec3 col = mix(iron * (0.5 + 0.5 * m), c, smoothstep(0.6, 1.0, m));
  return mix(uBg, col, smoothstep(0.0, 0.4, m));
}
