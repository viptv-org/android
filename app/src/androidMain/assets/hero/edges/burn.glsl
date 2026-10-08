vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float n = fbm(uv * vec2(5.0, 4.0) + vec2(0.0, uTime * 0.015));
  float th = m * 1.5 - 0.1 + (n - 0.5) * 1.6 * (1.0 - m * 0.6);
  float alive = smoothstep(0.49, 0.53, th);
  float charred = smoothstep(0.36, 0.49, th);
  float ember = exp(-pow((th - 0.49) / 0.03, 2.0)) * (0.5 + 0.8 * noise(fc / (5.0 * uDpr) + uTime * 1.5));
  vec3 col = mix(uBg, vec3(0.05, 0.035, 0.025) + c * 0.12, charred);
  col = mix(col, c * mix(0.6, 1.0, smoothstep(0.53, 0.7, th)), alive);
  return col + vec3(1.0, 0.42, 0.08) * ember * border(uv);
}
