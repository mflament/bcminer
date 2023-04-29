import {Component, ReactElement} from "react";

interface WebpuMinerControlsProps {
}

export class WebgpuMinerControls extends Component<WebpuMinerControlsProps, {}> {

    static create(props: WebpuMinerControlsProps): ReactElement {
        return <WebgpuMinerControls {...props}/>
    }

    constructor(props: Readonly<WebpuMinerControlsProps>) {
        super(props);
        this.state = {}
    }

    render() {
        return <div className={"GPUMinerController"}></div>;
    }

    componentDidUpdate(prevProps: Readonly<WebpuMinerControlsProps>) {
    }
}
