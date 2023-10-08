import {GPUContext} from "./support";
import {BlockData} from "../Sha256";
import {parseHex} from "../Utils";
import {WGSL_MINER} from "./wgsl/miner.wgsl";

export interface WebGPUMinerWorker {
    setup(hexData: string, threadNonces: number): void;

    mineChunk(baseNonce: Uint32Array, workgroups: number): Promise<number | undefined>;
}

export async function createMinerWorker(workgroupSize: number): Promise<WebGPUMinerWorker> {
    const context = await GPUContext.create({canvas: new OffscreenCanvas(2, 2)});
    const device = context.device;
    const queue = device.queue;

    const module = device.createShaderModule({label: 'miner module', code: WGSL_MINER});

    const buffers = {
        params: device.createBuffer({label: 'params', size: PARAMS_BUFFER_SIZE, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_DST}),
        baseNonce: device.createBuffer({label: 'baseNonce', size: 4, usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST}),
        matchResult: device.createBuffer({label: 'matchResult', size: 8, usage: GPUBufferUsage.STORAGE | GPUBufferUsage.COPY_SRC | GPUBufferUsage.COPY_DST}),
        matchDest: device.createBuffer({label: "matchDest", size: 8, usage: GPUBufferUsage.MAP_READ | GPUBufferUsage.COPY_DST})
    };

    const pipeline = await device.createComputePipelineAsync({
        label: 'hash',
        layout: 'auto',
        compute: {
            module, entryPoint: 'mine', constants: {WORKGROUP_SIZE: workgroupSize}
        }
    });

    const bindGroup = device.createBindGroup({
        label: 'buffers',
        layout: pipeline.getBindGroupLayout(0),
        entries: [
            {binding: 0, resource: {buffer: buffers.params}},
            {binding: 1, resource: {buffer: buffers.baseNonce}},
            {binding: 2, resource: {buffer: buffers.matchResult}},
        ]
    });

    const hostParamsBuffer = new Uint32Array(PARAMS_BUFFER_INTS);
    const resultsBuffer = buffers.matchResult, localResultBuffer = buffers.matchDest;
    return {
        setup(hexData: string, threadNonces: number) {
            const blockData = new BlockData(parseHex(hexData));
            let index = 0;
            hostParamsBuffer[index++] = threadNonces;
            hostParamsBuffer[index++] = blockData.data[16];
            hostParamsBuffer[index++] = blockData.data[17];
            hostParamsBuffer[index++] = blockData.data[18];
            for (let i = 0; i < 8; i++) hostParamsBuffer[index++] = blockData.midstate[i];
            hostParamsBuffer[index++] = blockData.hMaskOffset;
            hostParamsBuffer[index++] = blockData.hMask;
            queue.writeBuffer(buffers.params, 0, hostParamsBuffer);
        },
        async mineChunk(baseNonce: Uint32Array, workgroups: number) {
            // console.log(baseNonce)
            queue.writeBuffer(buffers.baseNonce, 0, baseNonce);

            let commandEncoder = device.createCommandEncoder();
            const passEncoder = commandEncoder.beginComputePass();
            passEncoder.setPipeline(pipeline);
            passEncoder.setBindGroup(0, bindGroup);
            passEncoder.dispatchWorkgroups(workgroups);
            passEncoder.end();
            commandEncoder.copyBufferToBuffer(resultsBuffer, 0, localResultBuffer, 0, 8);
            queue.submit([commandEncoder.finish()]);

            await queue.onSubmittedWorkDone();

            await localResultBuffer.mapAsync(GPUMapMode.READ);
            const matches = new Uint32Array(localResultBuffer.getMappedRange());
            const match = matches[0] ? matches[1] : undefined;
            localResultBuffer.unmap();
            if (match !== undefined) {
                commandEncoder = device.createCommandEncoder();
                commandEncoder.clearBuffer(resultsBuffer);
                queue.submit([commandEncoder.finish()]);
                await queue.onSubmittedWorkDone();
            }
            return match;
        }
    }
}

const PARAMS_BUFFER_INTS = 14;
const PARAMS_BUFFER_SIZE = PARAMS_BUFFER_INTS * 4;
