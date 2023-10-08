import {BlockData, newHashBuffer, nonceHasher} from "../Sha256";
import {BlockConfig} from "../BlockFetcher";
import {parseHex} from "../Utils";


const CHUNK_SIZE = 50_000;

export interface JSMinerStart {
    blockConfig: BlockConfig;
    startNonce: number;
    threadIndex: number;
    threadsCount: number;
}

export interface JSMinerResponse {
    threadIndex: number;
    totalHashes: number;
    matchedNonce?: number;
    matchedCount: number;
    matchedTime: number;
}

let running = false;

function isJSMinerStart(data: any): data is JSMinerStart {
    return data && typeof data === "object"
        && "blockConfig" in data && typeof data.blockConfig === "object"
        && "startNonce" in data && typeof data.startNonce === "number"
        && "threadIndex" in data && typeof data.threadIndex === "number";
}

self.onmessage = (e: MessageEvent) => {
    const data = e.data;
    if (data === "stop")
        running = false;
    else if (isJSMinerStart(data) && !running)
        mine(data);
}

function mine(task: JSMinerStart) {
    const threadIndex = task.threadIndex;
    let totalHashes = 0,
        matchedNonce: number | undefined = undefined,
        matchedCount = 0,matchedTime;
    const nonce = new Uint32Array([task.startNonce + task.threadIndex]);
    const threadsCount = task.threadsCount;
    const blockData = new BlockData(parseHex(task.blockConfig.data));
    const hash = newHashBuffer();
    const hasher = nonceHasher(blockData);
    const mineChunk = () => {
        let remainings  = CHUNK_SIZE;
        while (remainings > 0) {
            if (!running)
                return;
            hasher(nonce[0], hash);
            if (blockData.testHash(hash)) {
                matchedTime = performance.now();
                matchedCount++;
                matchedNonce = nonce[0];
            }
            totalHashes++;
            nonce[0] += threadsCount;
            remainings--;
        }

        const response: JSMinerResponse = {totalHashes, threadIndex, matchedCount, matchedNonce, matchedTime: performance.now()};
        self.postMessage(response);
        setTimeout(mineChunk);
    }
    running = true;
    mineChunk();
}
