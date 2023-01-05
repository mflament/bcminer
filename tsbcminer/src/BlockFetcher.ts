import {HEADER_INTS, swap} from './Sha256';
import {flipEndianness, parseHex, printHex} from "./Utils";

export interface BlockConfig {
    data: string;
    expectedNonce: number;
}

interface BlockchainData {
    hash: string;
    ver: number;
    prev_block: string;
    mrkl_root: string;
    time: number;
    bits: number;
    nonce: number;
}

const fetchConfig: RequestInit = {mode: "cors"};

export async function fetchBlockConfig(hash: string): Promise<BlockConfig> {
    const response = await fetch('https://blockchain.info/rawblock/' + hash, fetchConfig);
    if (response.ok) {
        const data: BlockchainData = await response.json();
        return {
            data: createHeader(data),
            expectedNonce: data.nonce
        };
    }
    throw response;
}

export async function fetchLastHash(): Promise<string> {
    const response = await fetch('https://blockchain.info/q/latesthash', fetchConfig);
    if (response.ok)
        return await response.text();
    throw response;
}

function createHeader(blockchainData: BlockchainData): string {
    const result = new Uint32Array(HEADER_INTS);
    result[0] = swap(blockchainData.ver);
    result.set(parseHex(flipEndianness(blockchainData.prev_block)), 1); // 32 bytes (8 ints)
    result.set(parseHex(flipEndianness(blockchainData.mrkl_root)), 9); //  32 bytes (8 ints)
    result[17] = swap(blockchainData.time);
    result[18] = swap(blockchainData.bits);
    result[19] = swap(blockchainData.nonce);
    return printHex(result);
}
