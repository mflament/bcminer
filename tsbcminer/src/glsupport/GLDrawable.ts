import { checkNull } from '../Utils';

export interface GLDrawable {
  bind(): void;
  draw(): void;
  delete(): void;
}

export function createDrawable(gl: WebGL2RenderingContext): GLDrawable {
  const vbo = checkNull(gl.createBuffer(), 'vbo');
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
  gl.bufferData(gl.ARRAY_BUFFER, QUAD, gl.STATIC_DRAW);

  const vao = checkNull(gl.createVertexArray(), 'vao');
  gl.bindVertexArray(vao);
  gl.enableVertexAttribArray(0);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 2 * 4, 0);
  gl.bindBuffer(gl.ARRAY_BUFFER, null);

  const ibo = checkNull(gl.createBuffer(), 'ibo');
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ibo);
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, QUAD_INDICES, gl.STATIC_DRAW);

  return {
    bind() {
      gl.bindVertexArray(vao);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ibo);
    },
    draw() {
      gl.drawElements(gl.TRIANGLES, QUAD_INDICES.length, gl.UNSIGNED_SHORT, 0);
    },
    delete() {
      gl.deleteBuffer(vbo);
      gl.deleteBuffer(ibo);
      gl.deleteVertexArray(vao);
    }
  };
}

// prettier-ignore
const QUAD = new Float32Array([
    -1.0, -1.0,
    1.0, -1.0,
    1.0, 1.0,
    -1.0, 1.0
]);
// prettier-ignore
const QUAD_INDICES = new Uint16Array([
    0, 1, 2,
    2, 3, 0
]);
