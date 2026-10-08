vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 cid;
  vec3 v = voro(uv * aspect() * 24.0, cid);
  vec3 ice = cur(uv + (vec2(hash(cid), hash(cid + 1.0)) - 0.5) * 0.015) * vec3(0.75, 0.88, 1.05) + vec3(0.1, 0.14, 0.18);
  ice += vec3(0.6, 0.8, 1.0) * smoothstep(0.06, 0.0, v.x) * 0.45;
  float k = m + (hash(cid + 2.0) - 0.5) * 0.25;
  vec3 col = mix(ice, c, smoothstep(0.55, 0.75, k));
  return mix(uBg, col, smoothstep(0.08, 0.25, k) * mix(0.6, 1.0, clamp(k, 0.0, 1.0)));
}
