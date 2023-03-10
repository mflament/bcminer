import {ChangeEventHandler, Component} from "react";
import {HashSelector} from "./HashSelector";
import {flipEndianness} from "./Utils";
import {IMiner, MinerId} from "./IMiner";
import {FSGLMiner} from "./gl/FSGLMiner";
import {BlockConfig} from "./BlockFetcher";

import "./BCMinerApplication.scss"
import {JSMiner} from "./js/JSMiner";
import {VSGLMiner} from "./gl/VSGLMiner";

// hash of block index 239711
const DEFAULT_HASH = flipEndianness("5c8ad782c007cc563f8db735180b35dab8c983d172b57e2c2701000000000000");

interface MinerControllerProps {
}

interface MinerControllerState {
    minerId: MinerId;
    blockConfig?: BlockConfig;
    matchedNonce?: number | null;
    macthedTimes?: number;
    totalHashes: number;
    hps: number;
}

export class BCMinerApplication extends Component<MinerControllerProps, MinerControllerState> {
    private _miner: IMiner;
    private _refreshTimeId?: number;

    constructor(props: Readonly<MinerControllerProps> | MinerControllerProps) {
        super(props);
        this.state = {minerId: "glfs", totalHashes: 0, hps: 0};
        this._miner = this.createMiner(this.state.minerId);
    }

    render() {
        const {minerId, totalHashes, hps, matchedNonce, macthedTimes} = this.state;
        const miner = this._miner;
        const setMiner: ChangeEventHandler<HTMLSelectElement> = e => this.setMiner(e.target.value as MinerId);
        return <div className={"BCMinerApplication"}>
            <HashSelector hash={DEFAULT_HASH} onBlockConfigChanged={this.blockConfigChanged}/>
            <div className="miner-config">
                Miner :
                <select onChange={setMiner} value={minerId}>
                    <option value="glfs">Webgl FS</option>
                    <option value="js">JS</option>
                </select>
                {miner?.controls}
            </div>

            <div className="mining-controls">
                <div>
                    <button onClick={this.toggle}>{miner.running ? "Stop" : "Start"}</button>
                </div>

                <div>
                    <span>Total hashes :</span>
                    <input disabled={true} value={formatNumber(totalHashes)}/>
                </div>

                <div>
                    <span>HPS :</span>
                    <input disabled={true} value={formatNumber(hps)}/>
                </div>

                {matchedNonce !== undefined &&
                    <div>
                        <span>Matched nonce : </span>
                        {matchedNonce === null ? "not found" : <strong>{matchedNonce.toString(16)} (matched {macthedTimes} times)</strong>}
                    </div>
                }
            </div>
        </div>;
    }

    private readonly toggle = () => {
        const miner = this._miner;
        const bc = this.state.blockConfig;
        if (miner.running) {
            miner.stop();
            self.clearTimeout(this._refreshTimeId);
        } else if (bc) {
            const startTime = performance.now();
            const getElapsed = () => (performance.now() - startTime) / 1000;
            this.setState({macthedTimes: 0, matchedNonce: undefined, hps: 0, totalHashes: 0});
            miner.start(bc, 0xB89BEB3A);
            const refresh = () => {
                const matchedNonce = miner.matchedNonce;
                const totalHashes = miner.totalHashes;
                const macthedTimes = miner.macthedTimes;
                const elapsed = getElapsed();
                const hps = elapsed === 0 ? 0 : totalHashes / elapsed;
                this.setState({matchedNonce, hps, totalHashes, macthedTimes});
                this._refreshTimeId = self.setTimeout(refresh, 1000);
            }
            refresh();
        }
        this.forceUpdate();
    }

    private readonly blockConfigChanged = (blockConfig: BlockConfig) => this.setState({blockConfig});

    private setMiner(minerId: MinerId) {
        this.setState(state => {
            if (state.minerId === minerId)
                return state;
            this._miner?.delete && this._miner.delete();
            this._miner = this.createMiner(minerId);
            return {...state, minerId}
        });
    }

    private createMiner(id: MinerId) {
        if (id === "js") return new JSMiner()
        if (id === "glfs")
            return new FSGLMiner()
        return new VSGLMiner();
    }
}

function formatNumber(n: number) {
    const mmn = n / 1_000_000;
    return mmn.toFixed(2);
}