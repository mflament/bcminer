import { checkNull } from '../Utils';

export interface GLTexture extends TextureConfig {
  readonly gltexture: WebGLTexture;
  readonly components: number;

  bind(): void;
  unbind(): void;
  delete(): void;
}

export interface TextureConfig {
  internalFormat: GLenum;
  format: GLenum;
  type: GLenum;
  width: number;
  height: number;
}

type PartialConfig = Omit<TextureConfig, 'width' | 'height'>;

export const TextureConfigs = {
  RGBA8: <PartialConfig>{
    internalFormat: WebGL2RenderingContext.RGBA8,
    format: WebGL2RenderingContext.RGBA,
    type: WebGL2RenderingContext.UNSIGNED_BYTE
  },
  R8: <PartialConfig>{
    internalFormat: WebGL2RenderingContext.R8,
    format: WebGL2RenderingContext.RED,
    type: WebGL2RenderingContext.UNSIGNED_BYTE
  },
  R8UI: <PartialConfig>{
    internalFormat: WebGL2RenderingContext.R8UI,
    format: WebGL2RenderingContext.RED_INTEGER,
    type: WebGL2RenderingContext.UNSIGNED_BYTE
  },
  R32UI: <PartialConfig>{
    internalFormat: WebGL2RenderingContext.R32UI,
    format: WebGL2RenderingContext.RED_INTEGER,
    type: WebGL2RenderingContext.UNSIGNED_INT
  },
  RGBA32UI: <PartialConfig>{
    internalFormat: WebGL2RenderingContext.RGBA32UI,
    format: WebGL2RenderingContext.RGBA_INTEGER,
    type: WebGL2RenderingContext.UNSIGNED_INT
  }
};

export function createDataTexture(gl: WebGL2RenderingContext, config: TextureConfig): GLTexture {
  const texture = checkNull(gl.createTexture());
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texImage2D(
    gl.TEXTURE_2D,
    0,
    config.internalFormat,
    config.width,
    config.height,
    0,
    config.format,
    config.type,
    null
  );
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, typeBytes(config.type));

  return {
    gltexture: texture,
    ...config,
    components: internalFormatComponents(config.internalFormat),
    bind() {
      gl.bindTexture(gl.TEXTURE_2D, texture);
    },
    unbind() {
      gl.bindTexture(gl.TEXTURE_2D, null);
    },
    delete() {
      gl.deleteTexture(texture);
    }
  };
}

function typeBytes(type: GLenum): number {
  switch (type) {
    case WebGL2RenderingContext.UNSIGNED_BYTE:
    case WebGL2RenderingContext.BYTE:
      return 1;
    case WebGL2RenderingContext.UNSIGNED_SHORT:
    case WebGL2RenderingContext.SHORT:
    case WebGL2RenderingContext.HALF_FLOAT:
      return 2;
    case WebGL2RenderingContext.UNSIGNED_INT:
    case WebGL2RenderingContext.INT:
    case WebGL2RenderingContext.FLOAT:
      return 4;
    default:
      throw new Error('Unsupported type ' + type);
  }
}

function internalFormatComponents(internalFormat: GLenum): number {
  switch (internalFormat) {
    case WebGL2RenderingContext.R11F_G11F_B10F:
    case WebGL2RenderingContext.R16F:
    case WebGL2RenderingContext.R16I:
    case WebGL2RenderingContext.R16UI:
    case WebGL2RenderingContext.R32F:
    case WebGL2RenderingContext.R32I:
    case WebGL2RenderingContext.R32UI:
    case WebGL2RenderingContext.R8:
    case WebGL2RenderingContext.R8I:
    case WebGL2RenderingContext.R8UI:
    case WebGL2RenderingContext.R8_SNORM:
      return 1;
    case WebGL2RenderingContext.RG16F:
    case WebGL2RenderingContext.RG16I:
    case WebGL2RenderingContext.RG16UI:
    case WebGL2RenderingContext.RG32F:
    case WebGL2RenderingContext.RG32I:
    case WebGL2RenderingContext.RG32UI:
    case WebGL2RenderingContext.RG8:
    case WebGL2RenderingContext.RG8I:
    case WebGL2RenderingContext.RG8UI:
    case WebGL2RenderingContext.RG8_SNORM:
      return 2;
    case WebGL2RenderingContext.RGB10_A2:
    case WebGL2RenderingContext.RGB10_A2UI:
    case WebGL2RenderingContext.RGB16F:
    case WebGL2RenderingContext.RGB16I:
    case WebGL2RenderingContext.RGB16UI:
    case WebGL2RenderingContext.RGB32F:
    case WebGL2RenderingContext.RGB32I:
    case WebGL2RenderingContext.RGB32UI:
    case WebGL2RenderingContext.RGB8:
    case WebGL2RenderingContext.RGB8I:
    case WebGL2RenderingContext.RGB8UI:
    case WebGL2RenderingContext.RGB8_SNORM:
    case WebGL2RenderingContext.RGB9_E5:
      return 3;
    case WebGL2RenderingContext.RGBA16F:
    case WebGL2RenderingContext.RGBA16I:
    case WebGL2RenderingContext.RGBA16UI:
    case WebGL2RenderingContext.RGBA32F:
    case WebGL2RenderingContext.RGBA32I:
    case WebGL2RenderingContext.RGBA32UI:
    case WebGL2RenderingContext.RGBA8:
    case WebGL2RenderingContext.RGBA8I:
    case WebGL2RenderingContext.RGBA8UI:
    case WebGL2RenderingContext.RGBA8_SNORM:
      return 4;
    default:
      throw new Error('Unhandled internal texture format ' + internalFormat);
  }
}
