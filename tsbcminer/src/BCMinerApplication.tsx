import {ChangeEventHandler, Component} from "react";
import {HashSelector} from "./HashSelector";
import {IMiner} from "./IMiner";
import {BlockConfig} from "./BlockFetcher";

import "./BCMinerApplication.scss"
import {JSMiner} from "./js";
import {WebGLMiner} from "./gl";
import {WebGPUMiner} from "./webgpu";

// hash of block index 239711
// const DEFAULT_HASH = flipEndianness("5c8ad782c007cc563f8db735180b35dab8c983d172b57e2c2701000000000000");
const DEFAULT_BLOCK: BlockConfig = {
    "hash": "00000000000001272c7eb572d183c9b8da350b1835b78d3f56cc07c082d78a5c",
    "data": "020000000affed3fc96851d8c74391c2d9333168fe62165eb228bced7e000000000000004277b65e3bd527f0ceb5298bdb06b4aacbae8a4a808c2c8aa414c20f252db801130dae516461011a3aeb9bb8",
    "expectedNonce": 3097226042
}

const minerFactories = {
    js: JSMiner.factory,
    webgl: WebGLMiner.factory,
    webgpu: navigator.gpu ? WebGPUMiner.factory : undefined
};
type MinerId = keyof typeof minerFactories;

interface MinerControllerProps {
}

interface MinerControllerState {
    minerId: MinerId;
    miner?: IMiner;
    blockConfig?: BlockConfig;
    matchedNonce?: number | null;
    matchedCount?: number;
    matchTime?: number;
    totalHashes: number;
    hps: number;
}

export class BCMinerApplication extends Component<MinerControllerProps, MinerControllerState> {
    private _refreshTimeId?: number;

    constructor(props: Readonly<MinerControllerProps> | MinerControllerProps) {
        super(props);
        this.state = {minerId: "loading" as MinerId, totalHashes: 0, hps: 0};
    }

    get miner() {
        return this.state.miner;
    }

    componentDidMount() {
        this.setMiner(navigator.gpu ? "webgpu" : "webgl");
    }

    render() {
        const {minerId, matchTime, blockConfig} = this.state;
        const miner = this.miner;
        const setMinerId: ChangeEventHandler<HTMLSelectElement> = e => this.setMiner(e.target.value as MinerId);
        return <div className={"BCMinerApplication"}>
            <HashSelector config={blockConfig || DEFAULT_BLOCK} onChange={this.blockConfigChanged}/>
            <div className="miner-config">
                Miner :
                <select onChange={setMinerId} value={minerId}>
                    {Object.entries(minerFactories).map(([id, factory]) => factory
                        ? <option key={id} value={id}>{factory?.name}</option>
                        : undefined)}
                </select>
                {miner?.controls}
            </div>
            {miner ? this.miningControls(miner) : <span>Loading...</span>}
        </div>;
    }

    private miningControls(miner: IMiner) {
        const {totalHashes, hps, matchedNonce, matchedCount, matchTime, blockConfig} = this.state;
        return <div className="mining-controls">
            <div>
                <button onClick={this.toggle} disabled={!blockConfig}>
                    {miner.running ? "Stop" : "Start"}
                </button>
            </div>

            <div>
                <span>Hashed</span>
                <span>{formatNumber(totalHashes)}</span>
            </div>

            <div>
                <span>HPS</span>
                <span>{formatNumber(hps)}</span>
            </div>

            {matchedNonce !== undefined &&
                <div>
                    <span>Matched nonce : </span>
                    {matchedNonce === null ? "not found" :
                        <strong>{matchedNonce.toString(16).toUpperCase()} (matched {matchedCount} times, matched
                            after {((matchTime || 0) / 1000)?.toFixed(1)} s)</strong>}
                </div>
            }
        </div>
    }

    private readonly toggle = () => {
        const miner = this.miner;
        if (!miner)
            return;
        const bc = this.state.blockConfig;
        if (miner.running) {
            miner.stop();
            self.clearTimeout(this._refreshTimeId);
        } else if (bc) {
            const startTime = performance.now();
            const getElapsed = () => (performance.now() - startTime) / 1000;
            this.setState({matchedCount: 0, matchedNonce: undefined, hps: 0, totalHashes: 0});
            miner.start(bc); // 3097226042
            const refresh = () => {
                const matchedNonce = miner.matchedNonce;
                if (matchedNonce !== undefined && matchedNonce !== bc.expectedNonce) {
                    console.error("Matched nonce ", matchedNonce, " is not expected nonce ", bc.expectedNonce);
                } else {
                    const totalHashes = miner.totalHashes;
                    const matchedCount = miner.matchedCount;
                    const matchTime = miner.matchTime;
                    const elapsed = getElapsed();
                    const hps = elapsed === 0 ? 0 : totalHashes / elapsed;
                    this.setState({matchedNonce, hps, totalHashes, matchedCount, matchTime});
                }
                this._refreshTimeId = self.setTimeout(refresh, 1000);
            }
            refresh();
        }
        this.forceUpdate();
    }

    private readonly blockConfigChanged = (blockConfig: BlockConfig) => {
        this.setState({blockConfig});
        console.log(JSON.stringify(blockConfig, null, '    '))
    }

    private setMiner(minerId: MinerId) {
        const minerFactory = minerFactories[minerId];
        if (!minerFactory)
            return;
        this.setState(state => {
            if (state.minerId === minerId)
                return state;
            state.miner?.delete && state.miner.delete();
            minerFactory.create().then(miner => this.setState({miner})).catch(e => console.error(e));
            return {...state, minerId, miner: undefined}
        });
    }

}

function formatNumber(n: number) {
    return Math.round(n / 1000).toLocaleString(undefined, {useGrouping: true}) + " K";
}