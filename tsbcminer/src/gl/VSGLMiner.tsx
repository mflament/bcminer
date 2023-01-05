import {ReactElement} from "react";
import {BlockConfig} from "../BlockFetcher";
import {
    createDataTexture,
    createFrameBuffer,
    createProgram,
    FrameBufferStatus,
    GLFrameBuffer,
    GLProgram,
    GLTexture,
    TextureConfigs
} from "../glsupport";
import {AbstractGLMiner, MinerUniforms} from "./AbstractGLMiner";

import VS from "./shaders/vsminer.vs.glsl"
import FS from "./shaders/vsminer.fs.glsl"

// const VERTEX_COUNT = 1_000_000;
const VERTEX_COUNT = 2;

export class VSGLMiner extends AbstractGLMiner {

    private readonly mineProgram: GLProgram;
    private readonly resultFramebuffer: GLFrameBuffer;
    private readonly resultTexture: GLTexture;
    private readonly resultBuffer: Uint32Array;

    private readonly uniforms: MinerUniforms;

    constructor() {
        super("glvs");
        const gl = this.gl;
        this.mineProgram = createProgram(gl, VS, FS);
        this.mineProgram.use();
        this.uniforms = {
            uData: this.mineProgram.uniformLocation('uData'),
            uMidstate: this.mineProgram.uniformLocation('uMidstate'),
            uHMaskOffset: this.mineProgram.uniformLocation('uHMaskOffset'),
            uHMask: this.mineProgram.uniformLocation('uHMask'),
            uNonce: this.mineProgram.uniformLocation('uNonce')
        };

        this.resultTexture = createDataTexture(this.gl, {...TextureConfigs.R32UI, width: 2, height: 1});

        this.resultFramebuffer = createFrameBuffer(gl);
        const fb = this.resultFramebuffer;
        fb.bind();
        fb.attach(this.resultTexture, gl.COLOR_ATTACHMENT0);
        gl.drawBuffers([WebGL2RenderingContext.COLOR_ATTACHMENT0]);
        const status = fb.status();
        if (status !== FrameBufferStatus.FRAMEBUFFER_COMPLETE)
            throw new Error('frame buffer error ' + FrameBufferStatus[status]);

        this.resultBuffer = new Uint32Array(2);
    }

    get controls(): ReactElement {
        return <></>
    }

    delete(): void {
        this.mineProgram.delete();
        this.resultFramebuffer?.delete();
        this.resultTexture?.delete();
    }

    protected starting(blockConfig: BlockConfig, startNonce?: number): void {
        const gl = this.gl;
        this.mineProgram.use();
        this.resultFramebuffer.bind();
        this.setUniforms(blockConfig, this.uniforms);
        gl.viewport(0, 0, 2, 1);
    }

    protected mineNonce(nonce: number): { matched?: number, hashed: number } {
        const {gl, resultFramebuffer, resultBuffer, uniforms} = this;
        gl.uniform1ui(uniforms.uNonce, nonce)
        gl.drawArrays(gl.POINTS, 0, VERTEX_COUNT);
        resultFramebuffer.read(resultBuffer, 0, WebGL2RenderingContext.COLOR_ATTACHMENT0, 0, 0, 2, 1);
        let matched;
        if (resultBuffer[0] !== 0)
            matched = resultBuffer[0];
        return {matched, hashed: VERTEX_COUNT};
    }

}