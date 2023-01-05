import {BlockData, newHashBuffer, nonceHasher} from "../Sha256";
import {BlockConfig} from "../BlockFetcher";
import {parseHex} from "../Utils";


const CHUNK_SIZE = 50_000;

export interface JSMinerStart {
    blockConfig: BlockConfig;
    startNonce: number;
    threadIndex: number;
}

export interface JSMinerResponse {
    threadIndex: number;
    totalHashes: number;
    matchedNonce?: number;
    matchedTimes: number;
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
        matchedTimes = 0,
        nonce = task.startNonce + task.threadIndex;
    const blockData = new BlockData(parseHex(task.blockConfig.data));
    const hash = newHashBuffer();
    const hasher = nonceHasher(blockData);
    const mineChunk = () => {
        let remainings  = CHUNK_SIZE;
        while (running) {
            hasher(nonce, hash);
            if (blockData.testHash(hash)) {
                matchedNonce = nonce;
                matchedTimes++;
            }
            totalHashes++;
            nonce++;
            if (nonce > 0xFFFFFFFF)
                nonce = 0;
            remainings--;
            if (remainings === 0) {
                const response: JSMinerResponse = {totalHashes, threadIndex, matchedTimes, matchedNonce};
                self.postMessage(response);
                setTimeout(mineChunk);
                break;
            }
        }
    }
    running = true;
    mineChunk();
}
