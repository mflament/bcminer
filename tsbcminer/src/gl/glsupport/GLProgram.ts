import { checkNull } from '../../Utils';

export interface GLProgram {
  readonly glprogram: WebGLProgram;
  use(): void;
  delete(): void;
  bindUniformBuffer(name: string, buffer: WebGLBuffer, location?: number): void;
  uniformLocation(name: string): WebGLUniformLocation | null;
}

export function createProgram(gl: WebGL2RenderingContext, vs: string, fs: string): GLProgram {
  const program = checkNull(gl.createProgram(), 'program');
  const shaders = [compileShader(gl, vs, ShaderType.VS), compileShader(gl, fs, ShaderType.FS)];
  for (const shader of shaders) {
    gl.attachShader(program, shader);
    gl.deleteShader(shader);
  }
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const log = gl.getProgramInfoLog(program);
    gl.deleteProgram(program);
    throw 'Error linking program\n' + log;
  }
  return {
    glprogram: program,
    use() {
      gl.useProgram(program);
    },
    bindUniformBuffer(name: string, buffer: WebGLBuffer, binding = 0): void {
      const index = gl.getUniformBlockIndex(program, name);
      if (index === gl.INVALID_INDEX) throw new Error('Invalid block index ' + index);
      gl.uniformBlockBinding(program, index, binding);
      gl.bindBufferBase(gl.UNIFORM_BUFFER, binding, buffer);
    },
    delete() {
      gl.deleteProgram(program);
    },
    uniformLocation(name: string) {
      return gl.getUniformLocation(program, name);
    },
  };
}

enum ShaderType {
  VS = WebGL2RenderingContext.VERTEX_SHADER,
  FS = WebGL2RenderingContext.FRAGMENT_SHADER
}

function compileShader(gl: WebGL2RenderingContext, source: string, type: ShaderType): WebGLShader {
  const shader = checkNull(gl.createShader(type), 'shader');
  gl.shaderSource(shader, source.trimStart());
  gl.compileShader(shader);
  if (gl.getShaderParameter(shader, gl.COMPILE_STATUS)) return shader;

  const log = gl.getShaderInfoLog(shader);
  gl.deleteShader(shader);
  source = source
    .split('\n')
    .map((l, i) => `${i.toLocaleString(undefined, { minimumIntegerDigits: 3 })} : ${l}`)
    .join('\n');
  throw `shader compile error ${source}\n${log}`;
}
