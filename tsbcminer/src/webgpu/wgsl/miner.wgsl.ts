// language=wgsl
import {SHA256} from "./SHA256.wgsl";

export const WGSL_MINER = `

override WORKGROUP_SIZE = 64;

const HEADER_INTS: u32 = 20;
const H_INTS: u32 = 8;
const BLOCK_INTS: u32 = 16;
const BUFFER_INTS: u32 = 64;

struct Params {
    count: u32,
    merkelRoot: u32,
    time: u32,
    nbits: u32,
    midstate: array<u32, H_INTS>,
    hMaskOffset: u32,
    hMask: u32,
}

@group(0) @binding(0) var<storage, read> params: Params;
@group(0) @binding(1) var<uniform> baseNonce: u32;
@group(0) @binding(2) var<storage, read_write> matches: array<u32, 2>;

//var<workgroup> groupMatches: array<u32, WORKGROUP_SIZE>;
var<private> workBuffer: array<u32, BUFFER_INTS>;
var<private> hash: array<u32, H_INTS>;

${SHA256}

fn testHash() -> bool {
    let hMaskOffset = params.hMaskOffset; 
    var sum = hash[hMaskOffset] & params.hMask;
    for (var i = hMaskOffset + 1; i < H_INTS; i++) {
        sum |= hash[i];
    }
    return sum == 0u;
}

fn createHash(nonce: u32) -> bool {
    workBuffer[0] = params.merkelRoot;// last int of merkel root
    workBuffer[1] = params.time;// time
    workBuffer[2] = params.nbits;// nbits
    workBuffer[3] = swap(nonce);// nonce
    //Padding
    workBuffer[4] = 0x80000000u;
    var i: u32;
    for (i = 5; i < BLOCK_INTS - 1; i++) { 
        workBuffer[i] = 0u;
    }
    // size (in bits) = 80 * 8 =  640
    workBuffer[BLOCK_INTS - 1] = 640u;
    
    // h = midstate
    for (i = 0; i < H_INTS; i++) { 
        hash[i] = params.midstate[i]; 
    }

    processBlock();
    
    // copy hash in start of workbuffer
    for (i = 0; i < H_INTS; i++) { 
        workBuffer[i] = hash[i]; 
    }
    workBuffer[8] = 0x80000000u;// padding
    for (i = 9; i < BLOCK_INTS - 1; i++) { 
        workBuffer[i] = 0u; 
    }
    workBuffer[BLOCK_INTS - 1] = 256u;// size (16 * 16)
    
    // h = DEFAULT_H
    for (i = 0; i < H_INTS; i++) {  
        hash[i] = DEFAULT_H[i]; 
    }
    processBlock();
    return testHash();
}

@compute @workgroup_size(WORKGROUP_SIZE, 1, 1)
fn mine(@builtin(global_invocation_id) globalId : vec3u) {
    let count = params.count;
    var nonce = baseNonce + globalId.x * count;
    for (var i = u32(0); i < count; i++) {
        if (createHash(nonce)) {
            matches[0] = 1;
            matches[1] = nonce;    
        }
        nonce++;
    }
}
`

