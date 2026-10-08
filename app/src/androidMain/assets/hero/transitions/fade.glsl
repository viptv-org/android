vec4 transition(vec2 uv, float p) { return mix(getFrom(uv), getTo(uv), ease(p)); }
