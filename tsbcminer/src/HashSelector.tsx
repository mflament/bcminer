import {ChangeEventHandler, Component} from "react";
import "./HashSelector.scss"
import {BlockConfig, fetchBlockConfig, fetchLastHash} from "./BlockFetcher";

interface HSelectorProps {
    config: BlockConfig,

    onChange(config?: BlockConfig): void;
}

interface HSelectorState {
    config: BlockConfig;
    hash: string;
    loading?: boolean;
}

export class HashSelector extends Component<HSelectorProps, HSelectorState> {

    constructor(props: Readonly<HSelectorProps> | HSelectorProps) {
        super(props);
        this.state = {config: props.config, hash: props.config.hash};
    }

    async componentDidMount() {
        this.props.onChange(this.state.config);
    }

    render() {
        const {hash, config, loading} = this.state;
        const {fetch, fetchLast} = this;
        const hashChanged: ChangeEventHandler<HTMLInputElement> = e => this.setState({hash: e.target.value});
        const hashLength = 64;
        return <div className={"HashSelector"}>
            <p>
                Hash: <input type="text" size={hashLength} minLength={hashLength} maxLength={hashLength} value={hash}
                             onChange={hashChanged}/>
                <button onClick={() => fetch(this.state.hash)} disabled={loading || config.hash === hash}>Get</button>
                <button onClick={fetchLast} disabled={loading}>Get last</button>
            </p>
            <p>
                Expected nonce <strong>{config.expectedNonce.toString(16).toUpperCase()}</strong>
            </p>
        </div>;
    }


    private readonly fetch = async (hash: string): Promise<void> => {
        this.setState({hash, loading: true});
        const block = await fetchBlockConfig(hash);
        this.setState(state => {
            if (state.hash === hash) {
                this.props.onChange(block)
                return {...state, block, loading: false};
            }
            return state;
        });
    };

    private readonly fetchLast = async (): Promise<void> => {
        this.setState({hash: "", loading: true});
        let hash = await fetchLastHash();
        await this.fetch(hash);
    }
}