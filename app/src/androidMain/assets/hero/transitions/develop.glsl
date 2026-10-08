// Develop: the incoming art resolves its highlights first and its shadows
// last, like a print in the tray, over a short dissolve.
vec4 transition(vec2 uv, float p) {
  vec4 a = getFrom(uv), b = getTo(uv);
  float l = luma(getToB(uv, 3.0).rgb);
  float thr = (1.0 - l) * 0.55;
  float k = smoothstep(thr, thr + 0.35, outCubic(p));
  return mix(a, b, k);
}
