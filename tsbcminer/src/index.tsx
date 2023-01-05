import {createRoot} from "react-dom/client";
import {BCMinerApplication} from "./BCMinerApplication";

const rootElement = document.createElement("div");
rootElement.id = "root";
document.body.append(rootElement);
const root = createRoot(rootElement);
root.render(<BCMinerApplication/>);
