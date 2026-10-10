"""Compare baseline/candidate hero ambient and edge passes with real desktop GLES.

Usage: python3 scripts/hero-render-benchmark.py BASELINE_ASSETS [CANDIDATE_ASSETS]
Requires Linux libEGL/libGLESv2. Output is JSON lines. This excludes Android,
art upload, transition/scene passes, mip generation and physical presentation.
"""
import ctypes as C
import hashlib
import json
import os
import statistics
import sys
import time
from pathlib import Path

os.environ.setdefault("EGL_PLATFORM", "surfaceless")
scale = float(os.environ.get("HERO_BENCH_SCALE", "1"))
assert 0 < scale <= 1, "HERO_BENCH_SCALE must be in (0, 1]"
trials = int(os.environ.get("HERO_BENCH_TRIALS", "12"))
assert 1 <= trials <= 1000
view_w, view_h = round(1920 * scale), round(950 * scale)
art_w, art_h = round(1280 * scale), round(720 * scale)
left, bottom = view_w - art_w, view_h - art_h
ground = (.043, .043, .047)
egl = C.CDLL("libEGL.so.1")
gl = C.CDLL("libGLESv2.so.2")
def bind(lib, name, result, *args):
    f = getattr(lib, name)
    f.restype = result
    f.argtypes = args
    return f
I, U, F, P = C.c_int, C.c_uint, C.c_float, C.c_void_p
get_display = bind(egl, "eglGetDisplay", P, P)
initialize = bind(egl, "eglInitialize", U, P, C.POINTER(I), C.POINTER(I))
choose = bind(egl, "eglChooseConfig", U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
create_context = bind(egl, "eglCreateContext", P, P, P, P, C.POINTER(I))
create_surface = bind(egl, "eglCreatePbufferSurface", P, P, P, C.POINTER(I))
make_current = bind(egl, "eglMakeCurrent", U, P, P, P, P)
display = get_display(None)
assert initialize(display, C.byref(I()), C.byref(I()))
config, count = P(), I()
assert choose(display, (I * 13)(0x3033, 1, 0x3040, 0x40, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038), C.byref(config), 1, C.byref(count)) and count.value
context = create_context(display, config, None, (I * 3)(0x3098, 3, 0x3038))
surface = create_surface(display, config, (I * 5)(0x3057, 1920, 0x3056, 950, 0x3038))
assert context and surface and make_current(display, surface, surface, context)
string = bind(gl, "glGetString", C.c_char_p, U)
create_shader = bind(gl, "glCreateShader", U, U)
shader_source = bind(gl, "glShaderSource", None, U, I, C.POINTER(C.c_char_p), P)
compile_shader = bind(gl, "glCompileShader", None, U)
shader_status = bind(gl, "glGetShaderiv", None, U, U, C.POINTER(I))
shader_log = bind(gl, "glGetShaderInfoLog", None, U, I, P, P)
create_program = bind(gl, "glCreateProgram", U)
attach = bind(gl, "glAttachShader", None, U, U)
link = bind(gl, "glLinkProgram", None, U)
program_status = bind(gl, "glGetProgramiv", None, U, U, C.POINTER(I))
use = bind(gl, "glUseProgram", None, U)
location = bind(gl, "glGetUniformLocation", I, U, C.c_char_p)
uniform1 = bind(gl, "glUniform1f", None, I, F)
uniform2 = bind(gl, "glUniform2f", None, I, F, F)
uniform3 = bind(gl, "glUniform3f", None, I, F, F, F)
uniformi = bind(gl, "glUniform1i", None, I, I)
gen_textures = bind(gl, "glGenTextures", None, I, C.POINTER(U))
texture_bind = bind(gl, "glBindTexture", None, U, U)
texture_image = bind(gl, "glTexImage2D", None, U, I, I, I, I, I, U, U, P)
texture_param = bind(gl, "glTexParameteri", None, U, U, I)
mipmap = bind(gl, "glGenerateMipmap", None, U)
attrib = bind(gl, "glGetAttribLocation", I, U, C.c_char_p)
enable = bind(gl, "glEnableVertexAttribArray", None, U)
pointer = bind(gl, "glVertexAttribPointer", None, U, I, U, U, I, P)
viewport = bind(gl, "glViewport", None, I, I, I, I)
scissor = bind(gl, "glScissor", None, I, I, I, I)
gl_enable = bind(gl, "glEnable", None, U)
gl_disable = bind(gl, "glDisable", None, U)
blend = bind(gl, "glBlendFunc", None, U, U)
draw = bind(gl, "glDrawArrays", None, U, I, I)
finish = bind(gl, "glFinish", None)
gl_error = bind(gl, "glGetError", U)
read = bind(gl, "glReadPixels", None, I, I, I, I, U, U, P)
quad = (F * 8)(-1, -1, 1, -1, -1, 1, 1, 1)
texture = U()
gen_textures(1, C.byref(texture))
texture_bind(0x0DE1, texture)
# Non-uniform, deterministic artwork exercises sampling and edge blending.
pixels = (C.c_ubyte * (art_w * art_h * 4))()
for y in range(art_h):
    for x in range(art_w):
        i = (y * art_w + x) * 4
        pixels[i:i+4] = (x % 256, y % 256, (x * 17 + y * 13) % 256, 255)
texture_image(0x0DE1, 0, 0x1908, art_w, art_h, 0, 0x1908, 0x1401, pixels)
texture_param(0x0DE1, 0x2801, 0x2703)
texture_param(0x0DE1, 0x2800, 0x2601)
texture_param(0x0DE1, 0x2802, 0x812F)
texture_param(0x0DE1, 0x2803, 0x812F)
mipmap(0x0DE1)
vertex = b"attribute vec2 aPos; varying vec2 vUv; void main(){vUv=aPos*.5+.5;gl_Position=vec4(aPos,0.,1.);}"
def shader(kind, source):
    s = create_shader(kind)
    shader_source(s, 1, C.byref(C.c_char_p(source)), None)
    compile_shader(s)
    ok = I()
    shader_status(s, 0x8B81, C.byref(ok))
    if not ok.value:
        log = C.create_string_buffer(8192)
        shader_log(s, 8192, None, log)
        raise RuntimeError(log.value.decode())
    return s
vs = shader(0x8B31, vertex)
def program(root, edge):
    parts = ("edge_common.glsl", f"edges/{edge}.glsl", "edge_main.glsl") if edge else ("edge_common.glsl", "ambient_main.glsl")
    source = "\n".join((root / part).read_text() for part in parts)
    p = create_program()
    attach(p, vs)
    attach(p, shader(0x8B30, source.encode()))
    link(p)
    ok = I()
    program_status(p, 0x8B82, C.byref(ok))
    assert ok.value, edge
    use(p)
    a = attrib(p, b"aPos")
    enable(a)
    pointer(a, 2, 0x1406, 0, 0, quad)
    uniformi(location(p, b"uScene"), 0)
    for key, values in {"uRes": (art_w,art_h), "uView": (view_w,view_h), "uOrigin": (left,bottom), "uFocus": (.62,.55)}.items():
        uniform2(location(p, key.encode()), *values)
    uniform3(location(p, b"uGround"), .043, .043, .047)
    uniform1(location(p, b"uDpr"), art_w / 1280)
    return p
viewport(640, 230, 1280, 720)
def paint(p, age, morph):
    use(p)
    uniform1(location(p, b"uTime"), age)
    uniform1(location(p, b"uMorph"), morph)
    uniform3(location(p, b"uGround"), *ground)
    draw(5, 0, 4)
def render(p, ambient, previous, age, morph, optimized):
    viewport(0, 0, view_w, view_h)
    if optimized:
        gl_enable(0x0C11)
        scissor(0, 0, left, view_h)
        paint(ambient, age, -1)
        scissor(left, 0, art_w, bottom)
        paint(ambient, age, -1)
        gl_disable(0x0C11)
    else:
        paint(ambient, age, -1)
    viewport(left, bottom, art_w, art_h)
    if morph >= 0:
        paint(previous, age, -1)
        gl_enable(0x0BE2)
        blend(0x0302, 0x0303)
    paint(p, age, morph)
    gl_disable(0x0BE2)
    finish()
    assert gl_error() == 0, "GLES rejected the rendering commands"
def capture(p, ambient, previous, age, morph, optimized):
    render(p, ambient, previous, age, morph, optimized)
    out = (C.c_ubyte * (view_w * view_h * 4))()
    read(0, 0, view_w, view_h, 0x1908, 0x1401, out)
    result = bytes(out)
    # A failed/empty draw must not masquerade as equivalent artwork.
    samples = result[::256]
    assert max(samples) - min(samples) >= 64, "Fixture artwork was not rendered"
    return result
roots = [Path(arg) for arg in sys.argv[1:]]
assert 1 <= len(roots) <= 2
def digest(root):
    h = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.suffix in (".glsl", ".json"):
            h.update(str(path.relative_to(root)).encode())
            h.update(path.read_bytes())
    return h.hexdigest()
print(json.dumps({"renderer": string(0x1F01).decode(), "scope": "offscreen ambient and edge passes; excludes Android compositor/upload/scene passes", "samples_per_variant": trials, "scale": scale, "source_hashes": [digest(r) for r in roots]}), flush=True)
ambient_programs = [program(root, None) for root in roots]
for item in json.loads((roots[0]/"index.json").read_text())["edges"]:
    edge = item["id"]
    ground = (.043, .043, .047)
    programs = [program(root, edge) for root in roots]
    previous_programs = [program(root, "watercolor" if edge != "watercolor" else "smoke") for root in roots]
    samples = [[] for _ in roots]
    for i, p in enumerate(programs):
        render(p, ambient_programs[i], previous_programs[i], 1, -1, i == 1)
    # Alternate order to avoid always giving one variant the warmer GPU.
    for trial in range(trials):
        order = range(len(roots)) if trial % 2 == 0 else reversed(range(len(roots)))
        for i in order:
            start = time.perf_counter()
            render(programs[i], ambient_programs[i], previous_programs[i], 1, -1, i == 1)
            samples[i].append((time.perf_counter() - start) * 1000)
    costs = [round(statistics.median(s), 3) for s in samples]
    differences = []
    if len(programs) == 2:
        for age, morph, oled in ((0,-1,False), (1,-1,False), (12,-1,False), (1,.25,False), (1,.75,False), (1,-1,True), (1,.5,True)):
            ground = (0, 0, 0) if oled else (.043, .043, .047)
            a, b = [capture(p, ambient_programs[i], previous_programs[i], age, morph, i == 1) for i, p in enumerate(programs)]
            differences.append(0 if a == b else max(abs(x-y) for x,y in zip(a,b)))
        assert max(differences) <= 1, (edge, differences)
    print(json.dumps({"edge":edge,"median_ms":costs,"max_channel_delta":max(differences, default=0)}), flush=True)
