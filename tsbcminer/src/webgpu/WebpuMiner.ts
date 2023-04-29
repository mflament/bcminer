import {ReactElement} from "react";
import {IMiner} from "../IMiner";
import {WebgpuMinerControls} from "./WebGPUMinerControls";
import {BlockConfig} from "../BlockFetcher";

export class WebpuMiner implements IMiner {
    readonly id = 'webgpu';

    private _totalHashes = 0;
    private _matchedNonce?: number | null;
    private _matchedCount = 0;
    private _matchTime = -1;
    private _running = false;

    constructor() {
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
        return WebgpuMinerControls.create({});
    }

    delete(): void {
    }

    start(blockConfig: BlockConfig, startNonce = 0): void {
        if (!navigator.gpu)
            throw new Error("No webgpu support");
        navigator.gpu.requestAdapter({powerPreference: "high-performance"}).then(adapter => {
            if (!adapter) throw new Error("No webgpu adapter");
            this.mine(blockConfig, startNonce, adapter)
        });
    }

    stop(): void {
    }

    private mine(blockConfig: BlockConfig, startNonce: number, adapter: GPUAdapter) {
        console.log("adpater", adapter);
    }
}