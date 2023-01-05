import {ChangeEventHandler, Component, ReactElement} from "react";
import {BlockConfig} from "../BlockFetcher";
import {
    createDataTexture,
    createDrawable,
    createFrameBuffer,
    createProgram,
    FrameBufferStatus,
    GLDrawable,
    GLFrameBuffer,
    GLProgram,
    GLTexture,
    TextureConfigs
} from "../glsupport";
import quadVS from "./shaders/quad.vs.glsl"

import fsminerUint from "./shaders/fsminer-uint.fs.glsl"
import fsminerUVec4 from "./shaders/fsminer-uvec4.fs.glsl"
import fsminerReduce from "./shaders/fsminer-reduce.fs.glsl"

// import vsMiner from "./shaders/vsminer.vs.glsl"
import {BlockData} from "../Sha256";
import {parseHex} from "../Utils";
import {AbstractGLMiner, MinerUniforms} from "./AbstractGLMiner";

const UVEC4 = false;
const REDUCED_SIZE = 64;
const REDUCE_FACTOR = 2;
const DEFAULT_TEXTURE_SIZE = 4096;

export class FSGLMiner extends AbstractGLMiner {

    private _textureSize = DEFAULT_TEXTURE_SIZE;

    private readonly mineProgram: GLProgram;
    private readonly reduceProgram: GLProgram;
    private readonly drawable: GLDrawable;
    private readonly resultFramebuffer: GLFrameBuffer;
    private readonly reduceFramebuffer: GLFrameBuffer;

    private resultTexture?: GLTexture;
    private reducedTexture?: GLTexture;
    private readonly resultBuffer: Uint32Array;

    private readonly uniforms: MinerUniforms & { uResultsSize: WebGLUniformLocation | null };

    constructor() {
        super("glfs");
        const gl = this.gl;
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

    get controls(): ReactElement {
        return <GLMinerControls textureSize={this._textureSize} onTextureSizeChange={ts => this._textureSize = ts}
                                disabled={this.running}/>
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

    protected starting(blockConfig: BlockConfig, startNonce?: number): void {
        const gl = this.gl;
        this.createTextures(this._textureSize);
        this.mineProgram.use();

        if (UVEC4) {
            this.setUvec4Uniforms(blockConfig);
        } else
            this.setUniforms(blockConfig, this.uniforms);
        gl.uniform1ui(this.uniforms.uResultsSize, this._textureSize);
    }

    protected mineNonce(nonce: number): { matched?: number, hashed: number } {
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

}

export interface FSGLMinerControlsProps {
    textureSize: number;

    onTextureSizeChange(textureSize: number): void;

    disabled?: boolean;
}

export class GLMinerControls extends Component<FSGLMinerControlsProps, { textureSize: number }> {

    private readonly textureSizeChanged: ChangeEventHandler<HTMLSelectElement> = e => {
        this.setState({textureSize: parseInt(e.target.value)}, () => this.props.onTextureSizeChange(this.state.textureSize))
    };

    constructor(props: Readonly<FSGLMinerControlsProps> | FSGLMinerControlsProps) {
        super(props);
        this.state = {textureSize: props.textureSize}
    }

    render() {
        const textureSize = this.state.textureSize;
        const textureMBytes = textureSize * textureSize * 4 / (1024 * 1024); // texture type : R32UI
        return <div className={"GLMinerController"}>
            Texture size : <select value={textureSize} onChange={this.textureSizeChanged}
                                   disabled={this.props.disabled}>
            {textureSizes.map(ts => <option key={ts} value={ts}>{ts}</option>)}</select> ({textureMBytes.toFixed(2)} MB)
        </div>;
    }

    componentDidUpdate(prevProps: Readonly<FSGLMinerControlsProps>) {
        if (prevProps.textureSize !== this.props.textureSize)
            this.setState({textureSize: this.props.textureSize});
    }
}

export function createTextureSizes() {
    const results: number[] = [];
    let size = 256;
    for (let i = 0; i < 9; i++) {
        results.push(size);
        size = size << 1;
    }
    return results;
}

const textureSizes = createTextureSizes();
