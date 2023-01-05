import {IMiner} from "../IMiner";
import {BlockConfig} from "../BlockFetcher";
import {ReactElement} from "react";
import {BlockData} from "../Sha256";
import {createGLContext, GLProgram} from "../glsupport";
import {parseHex} from "../Utils";

export interface MinerUniforms {
    uData: WebGLUniformLocation | null,
    uMidstate: WebGLUniformLocation | null,
    uHMaskOffset: WebGLUniformLocation | null,
    uHMask: WebGLUniformLocation | null,
    uNonce: WebGLUniformLocation | null;
}

export abstract class AbstractGLMiner implements IMiner {
    readonly id: "glfs" | "glvs";
    readonly gl: WebGL2RenderingContext;

    private _totalHashes = 0;
    private _matchedNonce?: number | null;
    private _matchedTime = 0;
    private _running = false;

    protected constructor(type: "glfs" | "glvs") {
        this.id = type;
        this.gl = createGLContext();
    }

    get running(): boolean {
        return this._running;
    }

    get macthedTimes(): number {
        return this._matchedTime;
    }

    get totalHashes(): number {
        return this._totalHashes;
    }

    get matchedNonce() {
        return this._matchedNonce;
    }

    abstract get controls(): ReactElement;

    start(blockConfig: BlockConfig, startNonce?: number): void {
        if (this.running)
            return;
        this._totalHashes = 0;
        this._matchedNonce = undefined;
        this._matchedTime = 0;
        this._running = true;

        let nonce = startNonce === undefined ? 0 : startNonce;
        this.starting(blockConfig, startNonce);
        const miningPass = () => {
            if (!this.running) return;

            const {hashed, matched} = this.mineNonce(nonce);
            this._totalHashes += hashed;
            nonce += hashed;

            if (matched !== undefined) {
                this._matchedNonce = matched;
                this._matchedTime++;
            } else if (nonce > 0xFFFFFFFF) {
                if (this._matchedNonce === undefined) {
                    this._matchedNonce = null;
                    return;
                }
                nonce = 0;
            }
            setTimeout(miningPass)
        }
        setTimeout(miningPass)
    }

    protected abstract starting(blockConfig: BlockConfig, startNonce?: number): void;

    protected abstract mineNonce(nonce: number): { matched?: number, hashed: number };

    stop(): void {
        this._running = false;
    }

    abstract delete(): void;

    protected getUniformLocations(program: GLProgram): MinerUniforms {
        return {
            uData: program.uniformLocation('uData'),
            uMidstate: program.uniformLocation('uMidstate'),
            uHMaskOffset: program.uniformLocation('uHMaskOffset'),
            uHMask: program.uniformLocation('uHMask'),
            uNonce: program.uniformLocation('uNonce')
        }
    }

    protected setUniforms(blockConfig: BlockConfig, uniforms: MinerUniforms) {
        const gl = this.gl;
        const blockData = new BlockData(parseHex(blockConfig.data));
        gl.uniform1uiv(uniforms.uData, blockData.data);
        gl.uniform1uiv(uniforms.uMidstate, blockData.midstate);
        gl.uniform1i(uniforms.uHMaskOffset, blockData.hMaskOffset);
        gl.uniform1ui(uniforms.uHMask, blockData.hMask);
    }
}