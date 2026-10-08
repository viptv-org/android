vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 cid;
  vec3 v = voro(uv * aspect() * 14.0, cid);
  float mc = edgeMask(v.yz / 14.0 / aspect());
  float vis = step(hash(cid + 3.1) * 0.8 + 0.04, mc * 1.3);
  vec2 drift = (vec2(hash(cid + 1.7), hash(cid + 9.2)) - 0.5) * 0.05 * (1.0 - mc);
  vec3 shard = cur(uv + drift + vec2(0.0, -0.03 * (1.0 - mc) * (1.0 - mc))) * mix(0.55, 1.0, mc);
  shard += vec3(0.9) * smoothstep(0.035, 0.0, v.x) * (1.0 - mc) * 0.6;
  return mix(mix(uBg, shard, vis), c, smoothstep(0.9, 1.0, m));
}
