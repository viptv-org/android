vec4 transition(vec2 uv, float p) {
  float e = ease(p);
  vec2 q = uv * aspect();
  float sweep = mix(-0.6, 2.0, p);
  float blob = exp(-pow((q.x * 0.8 + q.y * 0.4 - sweep) / 0.5, 2.0));
  float flick = fbm(q * 1.4 + vec2(uTime * 0.25, 0.0));
  float leak = blob * (0.6 + 0.8 * flick) * sin(PI * p);
  vec3 tint = mix(vec3(1.0, 0.35, 0.12), vec3(1.0, 0.85, 0.5), flick);
  vec3 col = mix(getFrom(uv).rgb, getTo(uv).rgb, smoothstep(0.25, 0.75, e + (blob - 0.5) * 0.2));
  col += tint * leak * 1.2;
  col = 1.0 - exp(-col * 1.15);                    // soft filmic shoulder
  return vec4(col / (1.0 - exp(-1.15)), 1.0);
}
