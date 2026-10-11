// Runs the app's own AGSL fill shaders (pulled out of ShaderSources.kt and SurgeSources.kt by
// extract_shaders.py into build/shaders/) in WebGL2, so the Quick slider's fills in the store art
// are the very shaders the phone runs, not lookalikes.
//
// AGSL is GLSL ES 3 with other type names and a top-left origin. The port is mechanical:
// float2/half4 -> vec2/vec4, `layout(color) uniform half4` -> `uniform vec4`, and main()'s
// fragCoord flipped from GL's bottom-left origin to the canvas's top-left one.
//
// One WebGL context draws every fill (Chromium keeps only 16 alive) and each result is copied
// into the 2D canvas that shows it.

export function agslToGlsl(src) {
  const s = src
    .replace(/layout\s*\(\s*color\s*\)\s*uniform\s+half4/g, 'uniform vec4')
    .replace(/\bhalf4\b/g, 'vec4').replace(/\bhalf3\b/g, 'vec3').replace(/\bhalf2\b/g, 'vec2').replace(/\bhalf\b/g, 'float')
    .replace(/\bfloat4\b/g, 'vec4').replace(/\bfloat3\b/g, 'vec3').replace(/\bfloat2\b/g, 'vec2')
    .replace(/\bint4\b/g, 'ivec4').replace(/\bint3\b/g, 'ivec3').replace(/\bint2\b/g, 'ivec2')
    .replace(/\bfloat2x2\b/g, 'mat2').replace(/\bfloat3x3\b/g, 'mat3')
    .replace(/vec4\s+main\s*\(\s*vec2\s+(\w+)\s*\)/, 'vec4 agslMain(vec2 $1)');
  return `#version 300 es
precision highp float;
uniform float u_canvasH;
out vec4 fragColor;
${s}
void main() {
  fragColor = agslMain(vec2(gl_FragCoord.x, u_canvasH - gl_FragCoord.y));
}`;
}

const VERT = `#version 300 es
in vec2 p;
void main() { gl_Position = vec4(p, 0.0, 1.0); }`;

// Uniforms that are lengths: given in dp, scaled to the target's pixels.
const LENGTHS = new Set(['origin', 'size', 'level']);

class Renderer {
  constructor() {
    this.canvas = document.createElement('canvas');
    const gl = this.canvas.getContext('webgl2', { premultipliedAlpha: true, preserveDrawingBuffer: true, antialias: false, alpha: true });
    if (!gl) throw new Error('WebGL2 unavailable');
    this.gl = gl;
    this.programs = new Map();
    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  }

  program(name, source) {
    if (this.programs.has(name)) return this.programs.get(name);
    const gl = this.gl;
    const prog = gl.createProgram();
    for (const [type, code] of [[gl.VERTEX_SHADER, VERT], [gl.FRAGMENT_SHADER, agslToGlsl(source)]]) {
      const sh = gl.createShader(type);
      gl.shaderSource(sh, code);
      gl.compileShader(sh);
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(`${name}: ${gl.getShaderInfoLog(sh)}`);
      gl.attachShader(prog, sh);
    }
    gl.bindAttribLocation(prog, 0, 'p');
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(`${name}: ${gl.getProgramInfoLog(prog)}`);
    const locs = {};
    const n = gl.getProgramParameter(prog, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) {
      const info = gl.getActiveUniform(prog, i);
      locs[info.name] = { loc: gl.getUniformLocation(prog, info.name), type: info.type };
    }
    const p = { prog, locs };
    this.programs.set(name, p);
    return p;
  }

  // Draws shader `name` over the whole of `target` (a 2D canvas), uniforms in dp.
  draw(name, target, uniforms, dpr) {
    const gl = this.gl;
    const src = shaderSources.get(name);
    if (!src) throw new Error('shader not loaded: ' + name);
    const { prog, locs } = this.program(name, src);
    if (this.canvas.width !== target.width || this.canvas.height !== target.height) {
      this.canvas.width = target.width;
      this.canvas.height = target.height;
    }
    gl.viewport(0, 0, target.width, target.height);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.useProgram(prog);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    if (locs.u_canvasH) gl.uniform1f(locs.u_canvasH.loc, target.height);
    for (const [k, value] of Object.entries(uniforms)) {
      const u = locs[k];
      if (!u) continue;
      let v = typeof value === 'string' ? hexToVec4(value) : value;
      if (LENGTHS.has(k)) v = Array.isArray(v) ? v.map((x) => x * dpr) : v * dpr;
      if (u.type === gl.FLOAT) gl.uniform1f(u.loc, v);
      else if (u.type === gl.FLOAT_VEC2) gl.uniform2fv(u.loc, v);
      else if (u.type === gl.FLOAT_VEC3) gl.uniform3fv(u.loc, v.slice(0, 3));
      else if (u.type === gl.FLOAT_VEC4) gl.uniform4fv(u.loc, v.length === 4 ? v : [...v, 1]);
      else if (u.type === gl.INT) gl.uniform1i(u.loc, v);
    }
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    const ctx = target.getContext('2d');
    ctx.clearRect(0, 0, target.width, target.height);
    ctx.drawImage(this.canvas, 0, 0);
  }
}

let renderer = null;
const shaderSources = new Map();

export async function loadShaders(names) {
  await Promise.all(names.map(async (name) => {
    if (shaderSources.has(name)) return;
    const res = await fetch(new URL(`../build/shaders/${name}.agsl`, import.meta.url));
    if (!res.ok) throw new Error('shader missing: ' + name + ' (run extract_shaders.py)');
    shaderSources.set(name, await res.text());
  }));
  renderer ??= new Renderer();
  for (const n of names) renderer.program(n, shaderSources.get(n));
}

// A 2D canvas of w x h dp that shows one shader; call .draw(uniforms) each frame.
export class ShaderCanvas {
  constructor(name, width, height, dpr = 2) {
    this.name = name;
    this.dpr = dpr;
    this.canvas = document.createElement('canvas');
    this.canvas.width = Math.round(width * dpr);
    this.canvas.height = Math.round(height * dpr);
    this.canvas.style.width = width + 'px';
    this.canvas.style.height = height + 'px';
    this.canvas.style.display = 'block';
  }
  draw(uniforms) {
    renderer.draw(this.name, this.canvas, uniforms, this.dpr);
  }
}

export function hexToVec4(hex, a = 1) {
  const v = hex.replace('#', '');
  const n = parseInt(v, 16);
  if (v.length === 8) return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, ((n >>> 24) & 255) / 255];
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a];
}
