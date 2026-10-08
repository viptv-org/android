vec3 edge(vec3 c, vec2 uv, float m, vec2 fc) {
  float level = floor((1.0 - m) * 4.0);
  float s = exp2(level + 1.0) * uDpr;
  vec2 buv = (floor(fc / s) + 0.5) * s / uRes;
  vec3 bc = blurred(buv, level);
  return mix(mix(uBg, bc, pow(edgeMask(buv), 1.3)), c, smoothstep(0.92, 1.0, m));
}
