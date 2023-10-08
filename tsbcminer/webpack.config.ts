import * as path from "path";
import HtmlWebpackPlugin from "html-webpack-plugin";
import 'webpack-dev-server';
import webpack from "webpack";

const mode = process.env.NODE_ENV === "production" ? "production" : "development"
const config: webpack.Configuration = {
    mode,
    entry: ['./src/index.tsx', './src/js/JSMinerWorker.ts', './src/webgpu/WebGPUMinerWorker.ts'],
    plugins: [
        new HtmlWebpackPlugin({
            title: "BCMiner",
            favicon: path.join(__dirname, 'public/favicon.ico'),
        })
    ],
    module: {
        rules: [
            {
                test: /\.([cm]?ts|tsx)$/,
                exclude: /node_modules/,
                loader: 'ts-loader'
            },
            {
                test: /\.m?js/,
                resolve: {
                    fullySpecified: false
                },
                loader: 'source-map-loader'
            },
            {
                test: /\.s[ac]ss$/i,
                use: [
                    // Creates `style` nodes from JS strings
                    "style-loader",
                    // Translates CSS into CommonJS
                    "css-loader",
                    // Compiles Sass to CSS
                    {loader:  "sass-loader", options: {sourceMap: false}},
                ],
            },
            {
                test: /\.css$/i,
                use: [
                    // Creates `style` nodes from JS strings
                    "style-loader",
                    // Translates CSS into CommonJS
                    "css-loader"
                ],
            }
        ]
    },
    resolve: {
        // Add `.ts` and `.tsx` as a resolvable extension.
        extensions: [".ts", ".tsx", ".js"],
        // Add support for TypeScripts fully qualified ESM imports.
        extensionAlias: {
            ".js": [".js", ".ts"],
            ".cjs": [".cjs", ".cts"],
            ".mjs": [".mjs", ".mts"]
        }
    },
    output: {
        filename: '[name].bundle.js',
        path: path.resolve(process.cwd(), 'dist'),
        clean: true
    },
    optimization: {
        splitChunks: {
            chunks: 'all',
        }
    },
    watchOptions: {
        ignored: ['**/node_modules'],
    },
    devtool: mode === "development" ? 'source-map' : false,
    devServer: mode === "development" ? {
        static: [{
            directory: path.join(__dirname, 'public'),
        }, {
            directory: path.join(__dirname, './public/effects'),
            publicPath: '/effects',
            serveIndex: true
        }],
        compress: true,
        host: "localhost",
        open: false
    } : undefined
};

export default config;
