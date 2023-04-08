import {ReactElement} from "react";
import {IMiner} from "../IMiner";

export abstract class WebGPUMiner implements IMiner {
    readonly id = 'webgpu';
    controls: ReactElement;
    readonly matchTime: number;
    readonly matchedCount: number;
    readonly matchedNonce: number | null;
    readonly running: boolean;
    readonly totalHashes: number;

    delete(): void {
    }

    start(blockConfig: BlockConfig, startNonce?: number): void {
    }

    stop(): void {
    }

}