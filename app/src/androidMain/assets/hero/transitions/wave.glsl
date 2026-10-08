vec4 transition(vec2 uv, float p) {
  float e = ease(p);
  float front = mix(-0.35, 1.35, e);
  float edge = front
    + 0.07 * sin(uv.y * 7.0 + uTime * 1.3 + p * 5.0)
    + 0.035 * sin(uv.y * 15.0 - uTime * 2.1)
    + 0.015 * sin(uv.y * 31.0 + uTime * 3.0);
  float dist = uv.x - edge;
  float crest = exp(-dist * dist * 90.0);
  float wake = exp(-max(-dist, 0.0) * 6.0) * step(dist, 0.0);
  vec2 refr = vec2(0.03 * crest, 0.012 * sin(uv.x * 40.0 - uTime * 4.0) * (crest + wake * 0.6));
  vec4 a = getFrom(uv + refr);
  vec4 b = getTo(uv - refr * 0.7 + vec2(0.0, 0.006 * sin(uv.x * 22.0 - uTime * 3.0) * wake));
  vec4 col = mix(b, a, smoothstep(-0.015, 0.015, dist));
  return col + vec4(vec3(0.85, 0.92, 1.0) * crest * 0.18, 0.0);
}
