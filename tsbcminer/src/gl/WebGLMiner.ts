import {IMiner, IMinerFactory} from "../IMiner";
import {BlockConfig} from "../BlockFetcher";
import {ReactElement} from "react";
import {BlockData} from "../Sha256";
import {
    createDataTexture,
    createDrawable,
    createFrameBuffer,
    createGLContext,
    createProgram,
    FrameBufferStatus,
    GLDrawable,
    GLFrameBuffer,
    GLProgram,
    GLTexture,
    TextureConfigs
} from "./glsupport";
import {parseHex} from "../Utils";
import {WebglMinerControls} from "./WebglMinerControls";
import quadVS from "./shaders/quad.vs.glsl";
import fsminerUVec4 from "./shaders/fsminer-uvec4.fs.glsl";
import fsminerUint from "./shaders/fsminer-uint.fs.glsl";
import fsminerReduce from "./shaders/fsminer-reduce.fs.glsl";

export interface WebGLMinerOptions {
    textureSize?: number;
}

export interface MinerUniforms {
    uData: WebGLUniformLocation | null,
    uMidstate: WebGLUniformLocation | null,
    uHMaskOffset: WebGLUniformLocation | null,
    uHMask: WebGLUniformLocation | null,
    uNonce: WebGLUniformLocation | null;
}

const UVEC4 = false;
const REDUCED_SIZE = 64;
const REDUCE_FACTOR = 2;
const DEFAULT_TEXTURE_SIZE = 4096;

export class WebGLMiner implements IMiner<WebGLMinerOptions> {

    static readonly factory: IMinerFactory<WebGLMinerOptions> = {
        name: 'WebGL Miner',
        async create(options?: WebGLMinerOptions) {
            return new WebGLMiner(options);
        }
    }

    readonly gl: WebGL2RenderingContext;

    private _textureSize: number;

    private readonly mineProgram: GLProgram;
    private readonly reduceProgram: GLProgram;
    private readonly drawable: GLDrawable;
    private readonly resultFramebuffer: GLFrameBuffer;
    private readonly reduceFramebuffer: GLFrameBuffer;

    private resultTexture?: GLTexture;
    private reducedTexture?: GLTexture;
    private readonly resultBuffer: Uint32Array;

    private readonly uniforms: MinerUniforms & { uResultsSize: WebGLUniformLocation | null };

    private _totalHashes = 0;
    private _matchedNonce?: number | null;
    private _matchedCount = 0;
    private _matchTime = -1;
    private _running = false;

    private constructor(options?: WebGLMinerOptions) {
        this._textureSize = options?.textureSize || DEFAULT_TEXTURE_SIZE;
        const gl = createGLContext();
        this.gl = gl;
        this.mineProgram = createProgram(gl, quadVS, UVEC4 ? fsminerUVec4 : fsminerUint);
        this.mineProgram.use();
        this.uniforms = {
            uData: this.mineProgram.uniformLocation('uData'),
            uMidstate: this.mineProgram.uniformLocation('uMidstate'),
            uHMaskOffset: this.mineProgram.uniformLocation('uHMaskOffset'),
            uHMask: this.mineProgram.uniformLocation('uHMask'),
            uResultsSize: this.mineProgram.uniformLocation('uResultsSize'),
            uNonce: this.mineProgram.uniformLocation('uNonce')
        };

        this.reduceProgram = createProgram(gl, quadVS, fsminerReduce);
        this.reduceProgram.use();
        let location = this.reduceProgram.uniformLocation('uResults');
        gl.uniform1i(location, 0);
        location = this.reduceProgram.uniformLocation('uReduceFactor');
        gl.uniform1i(location, REDUCE_FACTOR);

        this.drawable = createDrawable(gl);
        this.drawable.bind();

        this.resultFramebuffer = createFrameBuffer(gl);
        this.reduceFramebuffer = createFrameBuffer(gl);
        this.resultBuffer = new Uint32Array(REDUCED_SIZE * REDUCED_SIZE);
    }

    get options() {
        return {textureSize: this._textureSize};
    }

    get running(): boolean {
        return this._running;
    }

    get matchedCount(): number {
        return this._matchedCount;
    }

    get matchTime(): number {
        return this._matchTime;
    }

    get totalHashes(): number {
        return this._totalHashes;
    }

    get matchedNonce() {
        return this._matchedNonce;
    }

    get controls(): ReactElement {
        return WebglMinerControls.create({
            textureSize: this._textureSize,
            onTextureSizeChange: ts => this._textureSize = ts,
            disabled: this.running
        });
    }

    start(blockConfig: BlockConfig, startNonce?: number): void {
        if (this.running)
            return;
        this._totalHashes = 0;
        this._matchedNonce = undefined;
        this._matchedCount = 0;
        this._running = true;

        const nonce = new Uint32Array(1);
        nonce[0] = startNonce === undefined ? 0 : startNonce;
        let start = performance.now();
        this.starting(blockConfig, startNonce);

        const miningPass = () => {
            if (!this.running) return;

            const {hashed, matched} = this.mineNonce(nonce[0]);
            this._totalHashes += hashed;
            nonce[0] += hashed;

            if (matched !== undefined) {
                const now = performance.now();
                this._matchTime = now - start;
                start = now;
                this._matchedNonce = matched;
                this._matchedCount++;
            }
            setTimeout(miningPass)
        }
        setTimeout(miningPass)
    }

    stop():void {
        this._running = false;
    }

    delete(): void {
        this.mineProgram.delete();
        this.reduceProgram?.delete();

        this.drawable.delete();

        this.resultFramebuffer?.delete();
        this.resultTexture?.delete();

        this.reduceFramebuffer?.delete();
        this.reducedTexture?.delete();
    }

    private starting(blockConfig: BlockConfig, _startNonce?: number): void {
        const gl = this.gl;
        this.createTextures(this._textureSize);
        this.mineProgram.use();

        if (UVEC4) {
            this.setUvec4Uniforms(blockConfig);
        } else
            this.setUniforms(blockConfig, this.uniforms);
        gl.uniform1ui(this.uniforms.uResultsSize, this._textureSize);
    }

    private mineNonce(nonce: number): { matched?: number, hashed: number } {
        const {
            gl,
            mineProgram,
            reduceProgram,
            drawable,
            resultFramebuffer,
            reduceFramebuffer,
            resultBuffer,
            uniforms
        } = this;

        gl.viewport(0, 0, this._textureSize, this._textureSize);
        resultFramebuffer.bind();
        mineProgram.use();
        gl.uniform1ui(uniforms.uNonce, nonce)
        drawable.draw();

        reduceProgram.use();
        let textureSize = this._textureSize;
        let {srcBuffer, dstBuffer} = {srcBuffer: resultFramebuffer, dstBuffer: reduceFramebuffer};
        while (textureSize > REDUCED_SIZE) {
            textureSize = textureSize / REDUCE_FACTOR;
            gl.viewport(0, 0, textureSize, textureSize);
            dstBuffer.bind();
            const srcTexture = srcBuffer.attachment();
            srcTexture?.bind();
            drawable.draw();
            srcTexture?.unbind();

            const swap = srcBuffer;
            srcBuffer = dstBuffer;
            dstBuffer = swap;
        }

        srcBuffer.read(resultBuffer, 0, WebGL2RenderingContext.COLOR_ATTACHMENT0, 0, 0, textureSize, textureSize);
        let matched;
        for (let i = 0; i < resultBuffer.length; i++) {
            if (resultBuffer[i] !== 0) {
                matched = resultBuffer[i];
                break;
            }
        }
        return {matched, hashed: this._textureSize * this._textureSize};
    }


    private setUvec4Uniforms(blockConfig: BlockConfig): void {
        const {gl, uniforms} = this;
        const blockData = new BlockData(parseHex(blockConfig.data));
        gl.uniform4uiv(uniforms.uData, blockData.data);
        gl.uniform4uiv(uniforms.uMidstate, blockData.midstate);
        gl.uniform2i(uniforms.uHMaskOffset, Math.floor(blockData.hMaskOffset / 4), blockData.hMaskOffset % 4);
        gl.uniform1ui(uniforms.uHMask, blockData.hMask);
    }

    private readonly createTextures = (size: number): void => {
        this.resultTexture?.delete();
        this.resultTexture = this.createResultTexture(size);
        this.resultFramebuffer.bind();
        this.attachTexture(this.resultTexture, this.resultFramebuffer);

        this.reducedTexture?.delete();
        this.reducedTexture = this.createResultTexture(size / REDUCE_FACTOR);
        this.reduceFramebuffer.bind();
        this.attachTexture(this.reducedTexture, this.reduceFramebuffer);
    }

    private attachTexture(texture: GLTexture, frameBuffer: GLFrameBuffer): void {
        const gl = this.gl;
        frameBuffer.attach(texture, gl.COLOR_ATTACHMENT0);
        gl.drawBuffers([WebGL2RenderingContext.COLOR_ATTACHMENT0]);
        const status = frameBuffer.status();
        if (status !== FrameBufferStatus.FRAMEBUFFER_COMPLETE)
            throw new Error('frame buffer error ' + FrameBufferStatus[status]);
    }

    private createResultTexture(size: number): GLTexture {
        return createDataTexture(this.gl, {
            ...TextureConfigs.R32UI,
            width: size,
            height: size
        });
    }

    private getUniformLocations(program: GLProgram): MinerUniforms {
        return {
            uData: program.uniformLocation('uData'),
            uMidstate: program.uniformLocation('uMidstate'),
            uHMaskOffset: program.uniformLocation('uHMaskOffset'),
            uHMask: program.uniformLocation('uHMask'),
            uNonce: program.uniformLocation('uNonce')
        }
    }

    private setUniforms(blockConfig: BlockConfig, uniforms: MinerUniforms) {
        const gl = this.gl;
        const blockData = new BlockData(parseHex(blockConfig.data));
        // console.log([...blockData.data].map(d => d + 'u').join(", "));
        gl.uniform1uiv(uniforms.uData, blockData.data);
        gl.uniform1uiv(uniforms.uMidstate, blockData.midstate);
        gl.uniform1i(uniforms.uHMaskOffset, blockData.hMaskOffset);
        gl.uniform1ui(uniforms.uHMask, blockData.hMask);
    }
}