void main() {
  gl_FragColor = vec4(ambient(gl_FragCoord.xy / uView), 1.0);
}
