import { checkNull } from '../../Utils';

export interface GLUniformBuffer {
  readonly glbuffer: WebGLBuffer;
  update(data: ArrayBufferView, dstOffset?: number, srcOffset?: number, length?: number): void;
  delete(): void;
}

const UNIFORM_BUFFER = WebGL2RenderingContext.UNIFORM_BUFFER;

export function createUniformBuffer(
  gl: WebGL2RenderingContext,
  dataOrSize: ArrayBufferView | number,
  usage: GLenum = WebGL2RenderingContext.DYNAMIC_DRAW
): GLUniformBuffer {
  const buffer = checkNull(gl.createBuffer());

  gl.bindBuffer(UNIFORM_BUFFER, buffer);
  if (typeof dataOrSize === 'number') gl.bufferData(UNIFORM_BUFFER, dataOrSize, usage);
  else gl.bufferData(UNIFORM_BUFFER, dataOrSize, usage);
  gl.bindBuffer(UNIFORM_BUFFER, null);

  return {
    glbuffer: buffer,
    update(data: ArrayBufferView, dstOffset = 0, srcOffset = 0, length?: number) {
      gl.bindBuffer(UNIFORM_BUFFER, buffer);
      gl.bufferSubData(UNIFORM_BUFFER, dstOffset, data, srcOffset, length);
      gl.bindBuffer(UNIFORM_BUFFER, null);
    },
    delete() {
      gl.deleteBuffer(buffer);
    }
  };
}
