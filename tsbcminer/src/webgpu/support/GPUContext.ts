interface OptionsBase {
    adapter?: GPURequestAdapterOptions;
}

interface CanvasOptions extends OptionsBase {
    canvas?: HTMLCanvasElement | null,
}

interface OffscreenOptions extends OptionsBase {
    canvas: OffscreenCanvas | { width: number, height: number },
}

type ResizeCallback = (width: number, height: number) => void;

export class GPUContext<C extends HTMLCanvasElement | OffscreenCanvas = HTMLCanvasElement | OffscreenCanvas> {

    static async create(options?: CanvasOptions): Promise<GPUContext<HTMLCanvasElement>>;
    static async create(options: OffscreenOptions): Promise<GPUContext<OffscreenCanvas>>;
    static async create(options: CanvasOptions | OffscreenOptions | undefined): Promise<GPUContext<HTMLCanvasElement | OffscreenCanvas>> {
        let canvas: OffscreenCanvas | HTMLCanvasElement;
        if (isOffscreenOptions(options)) {
            canvas = options.canvas instanceof OffscreenCanvas
                ? options.canvas
                : new OffscreenCanvas(options.canvas.width, options.canvas.height);
        } else {
            if (!options?.canvas) {
                canvas = options?.canvas || document.createElement('canvas')
                document.body.appendChild(canvas);
            } else {
                canvas = options.canvas;
            }
        }
        const gpu = navigator.gpu;
        if (!gpu)
            throw new Error("navigator.gpu returned null");

        const adapter = await gpu.requestAdapter(options?.adapter);
        if (!adapter)
            throw new Error("gpu.requestAdapter returned null");

        const device = await adapter.requestDevice();
        if (!device)
            throw new Error("adapter.requestDevice returned null");

        return new GPUContext(canvas, gpu, adapter, device);
    }

    readonly canvasContext: GPUCanvasContext;
    readonly presentationFormat: GPUTextureFormat;

    private readonly _resizeObserver?: ResizeObserver;

    private _onResize?: ResizeCallback;

    private constructor(readonly canvas: C,
                        readonly gpu: GPU,
                        readonly adapter: GPUAdapter,
                        readonly device: GPUDevice) {
        const canvasContext = canvas.getContext("webgpu") as GPUCanvasContext;
        if (!canvasContext)
            throw new Error("No webgpu canvas context");
        this.canvasContext = canvasContext;

        this.presentationFormat = navigator.gpu.getPreferredCanvasFormat();
        canvasContext.configure({device, format: this.presentationFormat});

        if (canvas instanceof HTMLCanvasElement) {
            this._resizeObserver = new ResizeObserver(entries => {
                const {width, height} = entries[0].contentRect;
                this.resize(width, height);
            });
            this._resizeObserver.observe(canvas);
        }
    }

    dispose() {
        if (this._resizeObserver)
            this._resizeObserver?.unobserve(this.canvas as HTMLCanvasElement);
    }

    resize(width: number, height: number): void {
        this.canvas.width = width;
        this.canvas.height = height;
        if (this._onResize)
            this._onResize(width, height);
    }

    get onResize(): ResizeCallback | undefined {
        return this._onResize;
    }

    set onResize(callback: ResizeCallback | undefined) {
        this._onResize = callback;
    }
}

function isOffscreenOptions(o: CanvasOptions | OffscreenOptions | undefined): o is OffscreenOptions {
    const canvas = o?.canvas;
    return canvas instanceof OffscreenCanvas || (!!canvas && "width" in canvas && "height" in canvas && !(canvas instanceof HTMLCanvasElement));
}
