vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  vec2 cid;
  vec3 v = voro(uv * aspect() * 12.0, cid);
  vec2 cuv = v.yz / 12.0 / aspect();
  float mc = edgeMask(cuv);
  vec3 cc = blurred(cuv, 4.0);
  cc = mix(vec3(luma(cc)), cc, 1.8) * 1.3;
  float lead = smoothstep(0.03, 0.07, v.x);
  vec3 glass = cc * lead * (0.85 + 0.3 * fbm(uv * 30.0));
  float on = step(hash(cid + 5.0) * 0.5 + 0.1, mc);
  return mix(mix(uBg, glass * mix(0.5, 1.0, mc), on), c, smoothstep(0.65, 0.95, m));
}
