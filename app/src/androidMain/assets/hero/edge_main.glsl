void main() {
  vec2 fc = gl_FragCoord.xy - uOrigin;
  vec2 uv = vUv;
  uBg = ambient(gl_FragCoord.xy / uView);
  // Styles shape the left edge; every style meets the ground softly at the
  // rectangle's left border and fades plainly along the bottom.
  float low = smoothstep(0.0, 0.42, uv.y);
  vec3 col = mix(uBg, edge(cur(uv), uv, leftMask(uv), fc), smoothstep(0.0, 0.10, uv.x) * low);
  float a = 1.0;
  if (uMorph >= 0.0) {
    float n = fbm(uv * vec2(3.0, 2.5) + 3.1);
    a = smoothstep(n - 0.12, n + 0.12, uMorph * 1.3 - 0.15);
  }
  gl_FragColor = vec4(col, a);
}
