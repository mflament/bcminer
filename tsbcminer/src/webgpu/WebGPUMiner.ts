import {ReactElement} from "react";
import {IMiner, IMinerFactory} from "../IMiner";
import {WebgpuMinerControls} from "./WebGPUMinerControls";
import {BlockConfig} from "../BlockFetcher";
import {WebGPUMinerOptions} from "./WebGPUMinerOptions";
import {createMinerWorker, WebGPUMinerWorker} from "./WebGPUMinerWorker";

export class WebGPUMiner implements IMiner<WebGPUMinerOptions> {

    static readonly factory: IMinerFactory<WebGPUMinerOptions> = {
        name: 'WebGPU Miner',
        async create(options?: WebGPUMinerOptions): Promise<IMiner<WebGPUMinerOptions>> {
            return new WebGPUMiner(options);
        }
    };

    private _totalHashes = 0;
    private _matchedNonce?: number | null;
    private _matchedCount = 0;
    private _matchTime = -1;
    private _running = false;

    private readonly workgroupSize: number;
    private workgroups: number;
    private threadNonces: number;
    private worker?: WebGPUMinerWorker;

    private constructor(options?: WebGPUMinerOptions) {
        this.workgroupSize = options?.workgroupSize || 32;
        this.workgroups = options?.workgroups || 128;
        this.threadNonces = options?.threadNonces || 1024 * 4;
        createMinerWorker(this.workgroupSize).then(w => this.worker = w)
    }

    get options(): WebGPUMinerOptions {
        return {workgroupSize: this.workgroupSize, workgroups: this.workgroups};
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

    async start(blockConfig: BlockConfig, startNonce = 0) {
        if (this._running)
            return;

        const {workgroupSize, workgroups, threadNonces} = this;
        const chunkSize = workgroupSize * workgroups * threadNonces;
        console.log({workgroupSize, workgroups, threadNonces, chunkSize});
        this._matchedNonce = undefined;
        this._totalHashes = 0;
        this._matchedCount = 0;
        this._matchTime = -1;
        if (!this.worker)
            this.worker = await createMinerWorker(workgroupSize)
        const worker = this.worker;
        this._running = true;
        this.worker.setup(blockConfig.data, threadNonces);
        let nonce = new Uint32Array([startNonce]);

        let startTime = performance.now(), st2 = startTime;
        const times = {count: 0, total: 0};
        let secs = 0;

        const checkResult = (matchedNonce: number | undefined) => {
            const now = performance.now();
            const elapsed = now - st2;
            times.total += elapsed;
            times.count++;
            secs += elapsed / 1000;
            if (secs > 1) {
                console.log(times.total / times.count)
                secs = 0;
            }
            st2 = now;

            this._totalHashes += chunkSize;
            nonce[0] += chunkSize;
            if (matchedNonce !== undefined) {
                this._matchedNonce = matchedNonce;
                this._matchedCount++;
                const now = performance.now();
                this._matchTime = now - startTime;
                startTime = now;
            }
            if (this._running)
                mineChunk();
        }

        const mineChunk = () => worker.mineChunk(nonce, workgroups).then(checkResult);
        mineChunk().catch(e => {
            this._running = false;
            throw e;
        });
    }

    stop(): void {
        this._running = false;
    }

}