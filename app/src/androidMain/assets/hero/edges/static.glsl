vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float sn = hash(floor(fc / (1.5 * uDpr)) + fract(floor(uTime * 30.0) * 0.173) * vec2(171.0, 93.0));
  float roll = smoothstep(0.06, 0.0, abs(fract(uv.y - uTime * 0.12) - 0.5) - 0.44);
  float k = smoothstep(0.1, 0.95, m);
  vec3 snow = vec3(sn) * 0.6 * pow(m, 0.6) * border(uv);
  vec3 col = mix(snow, c * (1.0 - roll * 0.25 * (1.0 - k)), k);
  col *= 0.92 + 0.08 * sin(fc.y * 1.2 / uDpr);
  return mix(uBg, col, smoothstep(0.02, 0.15, m + sn * 0.08));
}
