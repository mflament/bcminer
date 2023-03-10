import {IMiner} from "../IMiner";
import {BlockConfig} from "../BlockFetcher";
import {ChangeEventHandler, ReactElement, useState} from "react";
import {JSMinerResponse, JSMinerStart} from "./JSMinerWorker";

export class JSMiner implements IMiner {
    readonly id = "js";

    private _running = false;
    private _matchedNonce?: number | null;

    private _workersCount = navigator.hardwareConcurrency;
    private _workers?: { worker: Worker, totalHashes: number, matchedTimes: number }[];

    start(blockConfig: BlockConfig, startNonce?: number): void {
        if (this._running) return;
        this._running = true;
        this._matchedNonce = undefined;
        startNonce = startNonce === undefined ? 0 : startNonce;
        this._workers = []
        for (let threadIndex = 0; threadIndex < this._workersCount; threadIndex++) {
            const worker = new Worker(new URL("./JSMinerWorker.ts", import.meta.url));
            const message: JSMinerStart = {startNonce, threadIndex, blockConfig};
            worker.onmessage = this.handleWorkerResponse
            worker.postMessage(message)
            this._workers.push({worker, totalHashes: 0, matchedTimes: 0})
        }
    }

    private readonly handleWorkerResponse = (e: { data: JSMinerResponse }) => {
        const {threadIndex, matchedTimes, matchedNonce, totalHashes} = e.data;
        const worker = this._workers && this._workers[threadIndex];
        if (!worker) return;
        worker.totalHashes = totalHashes;
        worker.matchedTimes = matchedTimes;
        if (matchedNonce !== undefined)
            this._matchedNonce = matchedNonce;
    }

    stop(): void {
        const workers = this._workers;
        if (workers)
            workers.forEach(w => w.worker.terminate());
        this._workers = undefined;
        this._running = false;
    }

    get running(): boolean {
        return this._running;
    }

    get totalHashes(): number {
        const workers = this._workers;
        if (!workers) return 0;
        return workers.reduce((total, w) => total + w.totalHashes, 0);
    }

    get macthedTimes(): number {
        const workers = this._workers;
        if (!workers) return 0;
        return workers.reduce((total, w) => total + w.matchedTimes, 0);
    }

    get matchedNonce(): number | null | undefined {
        return this._matchedNonce;
    }

    get controls(): ReactElement {
        return <JSMinerControls workers={this._workersCount} onWorkersChanged={this.setWorkerCount}
                                disabled={this.running}/>
    }

    private readonly setWorkerCount = (workers: number) => this._workersCount = workers;

}

function JSMinerControls(props: { onWorkersChanged(workers: number): void, workers: number, disabled: boolean }) {
    const [workers, setWorkers] = useState(props.workers);
    const {onWorkersChanged, disabled} = props;
    const workersChanged : ChangeEventHandler<HTMLInputElement> = e => {
        const newWorkers = e.target.valueAsNumber;
        setWorkers(newWorkers);
        props.onWorkersChanged(newWorkers);
    }
    return <div className="jsminer-controls">
        <input type="range" min={1} max={navigator.hardwareConcurrency} step={1} value={workers}
               onChange={workersChanged} disabled={disabled}/>
        <span><strong>{workers}</strong> workers</span>
    </div>
}