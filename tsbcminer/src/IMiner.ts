import {ReactElement} from "react";
import {BlockConfig} from "./BlockFetcher";

export type MinerId = "webgl" | "webgpu" | "js";

export interface IMiner {
    id: MinerId;
    controls: ReactElement;

    start(blockConfig: BlockConfig, startNonce?: number): void;

    stop(): void;

    readonly running: boolean;
    readonly totalHashes: number;

    // undefined while mining, null if not found, the matched nonce otherwise
    readonly matchedNonce?: number | null;

    /**
     * number of time the nonce was matched (since miner will continue once matched, and rollover int32)
     */
    readonly matchedCount: number

    readonly matchTime: number

    delete?(): void;
}