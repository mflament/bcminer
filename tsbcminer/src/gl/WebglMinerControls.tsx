import {ChangeEventHandler, Component, ReactElement} from "react";

interface WebglMinerControlsProps {
    textureSize: number;
    disabled?: boolean;

    onTextureSizeChange(textureSize: number): void;
}

export class WebglMinerControls extends Component<WebglMinerControlsProps, { textureSize: number }> {

    static create(props: WebglMinerControlsProps): ReactElement {
        return <WebglMinerControls {...props}/>
    }

    private readonly textureSizeChanged: ChangeEventHandler<HTMLSelectElement> = e => {
        this.setState({textureSize: parseInt(e.target.value)}, () => this.props.onTextureSizeChange(this.state.textureSize))
    };

    constructor(props: Readonly<WebglMinerControlsProps>) {
        super(props);
        this.state = {textureSize: props.textureSize}
    }

    render() {
        const textureSize = this.state.textureSize;
        const textureMBytes = textureSize * textureSize * 4 / (1024 * 1024); // texture type : R32UI
        return <div className={"GLMinerController"}>Texture size :
            <select value={textureSize} onChange={this.textureSizeChanged} disabled={this.props.disabled}>
                {textureSizes.map(ts => <option key={ts} value={ts}> {ts} </option>)}
            </select> ({textureMBytes.toFixed(2)} MB)
        </div>;
    }

    componentDidUpdate(prevProps: Readonly<WebglMinerControlsProps>) {
        if (prevProps.textureSize !== this.props.textureSize)
            this.setState({textureSize: this.props.textureSize});
    }
}

export function createTextureSizes() {
    const results: number[] = [];
    let size = 256;
    for (let i = 0; i < 9; i++) {
        results.push(size);
        size = size << 1;
    }
    return results;
}

const textureSizes = createTextureSizes();