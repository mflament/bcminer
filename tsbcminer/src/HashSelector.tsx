import {ChangeEventHandler, Component} from "react";
import "./HashSelector.scss"
import {BlockConfig, fetchBlockConfig, fetchLastHash} from "./BlockFetcher";

interface HSelectorProps {
    hash?: string

    onBlockConfigSearch(): void;

    onBlockConfigChanged(config?: BlockConfig): void
}

interface HSelectorState {
    hash: string;
    block?: BlockConfig;
    fetching: boolean;
}

export class HashSelector extends Component<HSelectorProps, HSelectorState> {

    constructor(props: Readonly<HSelectorProps> | HSelectorProps) {
        super(props);
        this.state = {hash: props.hash || "", fetching: false};
    }

    render() {
        const {hash, block, fetching} = this.state;
        const {fetch, fetchLast} = this;
        const hashChanged: ChangeEventHandler<HTMLInputElement> = e => this.setState({hash: e.target.value});
        const hashLength = 64;
        return <div className={"HashSelector"}>
            <p>
                Hash: <input type="text" size={hashLength} minLength={hashLength} maxLength={hashLength} value={hash}
                             onChange={hashChanged}/>
                <button onClick={() => hash && fetch(hash)} disabled={!hash || fetching}>Get</button>
                <button onClick={fetchLast} disabled={fetching}>Get last</button>
            </p>
            <p>
                Expected nonce <strong>{block?.expectedNonce?.toString(16).toUpperCase()}</strong>
            </p>
        </div>;
    }

    async componentDidMount() {
        if (this.state.hash)
            await this.fetch(this.state.hash);
    }

    private readonly fetch = async (hash: string): Promise<void> => {
        this.setState({hash, fetching: true});
        this.props.onBlockConfigSearch();
        const block = await fetchBlockConfig(hash);
        this.setState(state => {
            if (state.hash === hash) return {...state, block, fetching: false};
            return state;
        }, () => this.props.onBlockConfigChanged(this.state.block));
    };

    private readonly fetchLast = async (): Promise<void> => {
        this.setState({hash: "", fetching: true});
        let hash = await fetchLastHash();
        await this.fetch(hash);
    }
}