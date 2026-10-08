vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 px = vec2(1.5 * uDpr) / uRes;
  float gx = luma(cur(uv + vec2(px.x, 0.0))) - luma(cur(uv - vec2(px.x, 0.0)));
  float gy = luma(cur(uv + vec2(0.0, px.y))) - luma(cur(uv - vec2(0.0, px.y)));
  float ink = smoothstep(0.06, 0.18, length(vec2(gx, gy)));
  vec3 post = floor(c * 5.0 + 0.5) / 5.0;
  post = mix(vec3(luma(post)), post, 1.3);
  vec3 toon = mix(post * (1.0 - ink * 0.85), c, smoothstep(0.65, 1.0, m));
  vec3 sketch = mix(uBg, vec3(0.92, 0.9, 0.86) * (0.3 + luma(c)), ink * smoothstep(0.0, 0.15, m));
  return mix(sketch, toon, smoothstep(0.2, 0.55, m));
}
