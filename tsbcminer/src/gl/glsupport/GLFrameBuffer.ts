import { checkNull } from '../../Utils';
import { type DataBuffer } from './DataBuffer';
import { type GLTexture } from './GLTexture';

export interface GLFrameBuffer {
  bind(): void;
  unbind(): void;
  attach(texture: GLTexture, attachmentPoint?: GLenum, level?: number): void;
  attachment(attachmentPoint?: GLenum): GLTexture | undefined;
  delete(): void;
  status(): GLenum;
  read(
    target: DataBuffer,
    targetOffset?: number,
    attachment?: GLenum,
    x?: number,
    y?: number,
    width?: number,
    height?: number
  ): void;
}

export function createFrameBuffer(gl: WebGL2RenderingContext): GLFrameBuffer {
  const fb = checkNull(gl.createFramebuffer());
  const attachments: { [attachment: GLenum]: GLTexture } = {};
  return {
    bind() {
      gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
    },
    unbind() {
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    },
    attach(texture: GLTexture, attachmentPoint = WebGL2RenderingContext.COLOR_ATTACHMENT0, level = 0) {
      gl.framebufferTexture2D(gl.FRAMEBUFFER, attachmentPoint, gl.TEXTURE_2D, texture.gltexture, level);
      attachments[attachmentPoint] = texture;
    },
    attachment(attachmentPoint = WebGL2RenderingContext.COLOR_ATTACHMENT0): GLTexture | undefined {
      return attachments[attachmentPoint];
    },
    read(
      target: DataBuffer,
      targetOffset = 0,
      attachment = WebGL2RenderingContext.COLOR_ATTACHMENT0,
      x = 0,
      y = 0,
      width?: number,
      height?: number
    ) {
      const texture = attachments[attachment];
      if (!texture) throw new Error('No texture attached to ' + attachment);
      texture.bind();
      gl.readBuffer(attachment);
      width = width === undefined ? texture.width : width;
      height = height === undefined ? texture.height : height;
      gl.readPixels(x, y, width, height, texture.format, texture.type, target, targetOffset);
    },
    delete() {
      gl.deleteFramebuffer(fb);
    },
    status(): GLenum {
      return gl.checkFramebufferStatus(gl.FRAMEBUFFER);
    }
  };
}
