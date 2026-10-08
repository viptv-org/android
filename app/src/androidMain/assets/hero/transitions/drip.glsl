vec4 transition(vec2 uv, float p) {
  float n = 0.55 * noise(vec2(uv.x * 9.0, 1.3)) + 0.3 * noise(vec2(uv.x * 27.0, 7.1)) + 0.15 * hash1(floor(uv.x * 140.0));
  float yTop = 1.0 - uv.y;
  float front = p * 1.75 - n * 0.75;          // how far the melt has run down this column
  float run = max(front, 0.0);
  vec2 aUv = vec2(uv.x, uv.y + run * run * 0.9);   // old frame slides down, accelerating
  float inB = smoothstep(front, front - 0.012, yTop);
  vec4 a = getFrom(aUv);
  a.rgb *= 1.0 - 0.35 * clamp(run, 0.0, 1.0);
  vec4 b = getTo(vec2(uv.x, uv.y - 0.05 * (1.0 - p)));
  float lip = exp(-pow((yTop - front) / 0.018, 2.0)) * step(0.001, p) * (1.0 - p);
  vec4 col = mix(a, b, inB);
  return col + vec4(vec3(1.0, 0.95, 0.85) * lip * 0.35, 0.0);
}
