import {ReactElement} from "react";
import {BlockConfig} from "./BlockFetcher";

export interface IMinerFactory<O extends any = any> {
    readonly name: string;

    create(options?: O): Promise<IMiner<O>>;
}

export interface IMiner<O extends any = any> {
    readonly options: O;

    readonly controls: ReactElement;

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
