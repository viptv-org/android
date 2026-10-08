void main() {
  vec2 fc = gl_FragCoord.xy - uOrigin;
  vec2 uv = vUv;
  uBg = ambient(gl_FragCoord.xy / uView);
  vec3 col = edge(cur(uv), uv, edgeMask(uv), fc);
  float a = 1.0;
  if (uMorph >= 0.0) {
    float n = fbm(uv * vec2(3.0, 2.5) + 3.1);
    a = smoothstep(n - 0.12, n + 0.12, uMorph * 1.3 - 0.15);
  }
  gl_FragColor = vec4(col, a);
}
