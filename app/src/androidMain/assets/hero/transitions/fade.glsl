// Baseline dissolve, also used for changes that arrive while browsing.
vec4 transition(vec2 uv, float p) { return mix(getFrom(uv), getTo(uv), outCubic(p)); }
