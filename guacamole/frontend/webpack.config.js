/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

const path = require('path');
const AngularTemplateCacheWebpackPlugin = require('angular-templatecache-webpack-plugin');
const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const DependencyListPlugin = require('./plugins/dependency-list-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const webpack = require('webpack');

module.exports = {

    bail: true,
    mode: 'production',
    stats: 'minimal',

    output: {
        path: path.resolve(__dirname, '../src/main/resources/static'),
        filename: 'guacamole.[contenthash].js',
    },

    // Generate source maps
    devtool: 'source-map',

    // webpack-dev-server configuration (npm run serve)
    devServer: {
        port: 3000,
        hot: false,
        liveReload: true,
        static: {
            directory: path.resolve(__dirname, 'src'),
        },
        proxy: [
            {
                context: ['/websocket-tunnel'],
                target: 'http://localhost:8080',
                changeOrigin: true,
                ws: true
            },
            {
                context: ['/api', '/tunnel', '/guacamole', '/app.css', '/app.js', '/app/ext', '/translations'],
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        ],
        devMiddleware: {
            publicPath: '/',
        },
        historyApiFallback: true,
        client: {
            overlay: {
                errors: true,
                warnings: false,
            },
        },
    },

    // Entry point for the Guacamole webapp is the "index" AngularJS module
    entry: './src/app/index/indexModule.js',

    module: {
        rules: [

            // Automatically extract imported CSS for later reference within separate CSS file
            {
                test: /\.css$/i,
                use: [
                    MiniCssExtractPlugin.loader,
                    {
                        loader: 'css-loader',
                        options: {
                            import: false,
                            url: false
                        }
                    }
                ]
            },

            /*
             * Necessary to be able to use angular 1 with webpack as explained in https://github.com/webpack/webpack/issues/2049
             */
            {
                test: require.resolve('angular'),
                loader: 'exports-loader',
                options: {
                    type: 'commonjs',
                    exports: 'single window.angular'
                }
            }

        ]
    },
    optimization: {
        minimizer: [

            // Minify JavaScript using TerserPlugin (ES5-compatible output)
            new TerserPlugin({
                parallel: true,
                exclude: /templates\.js$/,
                terserOptions: {
                    ecma: 5,
                    compress: {
                        drop_console: false
                    },
                    output: {
                        comments: false
                    }
                },
                extractComments: false
            }),

            new CssMinimizerPlugin()

        ],
        splitChunks: {
            cacheGroups: {

                // Bundle CSS as one file
                styles: {
                    name: 'styles',
                    test: /\.css$/,
                    chunks: 'all',
                    enforce: true
                }

            }
        }
    },
    plugins: [

        new AngularTemplateCacheWebpackPlugin({
            module: 'templates-main',
            root: 'app/',
            source: 'src/app/**/*.html',
            standalone: true
        }),

        // Post-process templates.js: normalize Windows backslash paths to
        // forward slashes within $templateCache.put() keys. AngularJS always
        // uses forward slashes for $templateCache.get() regardless of platform.
        {
            apply: function(compiler) {
                compiler.hooks.emit.tapAsync('NormalizeTemplatePaths', function(compilation, callback) {
                    for (var filename in compilation.assets) {
                        if (/^templates\.js$/.test(filename)) {
                            var asset = compilation.assets[filename];
                            var source = asset.source();
                            // Replace backslashes only within template cache keys
                            var normalized = source.replace(
                                /\$templateCache\.put\('([^']+)'/g,
                                function(match, key) {
                                    return "$templateCache.put('" + key.replace(/\\/g, '/') + "'";
                                }
                            );
                            compilation.assets[filename] = {
                                source: function() { return normalized; },
                                size: function() { return normalized.length; }
                            };
                        }
                    }
                    callback();
                });
            }
        },

        // Automatically clean out output directory, preserving guacamole-common-js
        new CleanWebpackPlugin({
            cleanOnceBeforeBuildPatterns: ['**/*', '!guacamole-common-js', '!guacamole-common-js/**']
        }),

        // Copy static files to output directory
        new CopyPlugin([
            { from: 'app/**/*' },
            { from: 'fonts/**/*' },
            { from: 'images/**/*' },
            { from: 'layouts/**/*' },
            { from: 'verifyCachedVersion.js' },
            { from: 'translations/**/*' }
        ], {
            context: 'src/'
        }),

        // Copy core libraries for global inclusion
        new CopyPlugin([
            { from: 'angular/angular.min.js' },
            { from: 'blob-polyfill/Blob.js' },
            { from: 'datalist-polyfill/datalist-polyfill.min.js' },
            { from: 'jquery/dist/jquery.min.js' },
            { from: 'lodash/lodash.min.js' }
        ], {
            context: 'node_modules/'
        }),

        // Generate index.html from template
        new HtmlWebpackPlugin({
            inject: false,
            template: 'src/index.html'
        }),

        // Extract CSS from Webpack bundle as separate file
        new MiniCssExtractPlugin({
            filename: 'guacamole.[contenthash].css',
            chunkFilename: '[id].guacamole.[contenthash].css'
        }),

        // List all bundled node modules for sake of automatic LICENSE file
        // generation / sanity checks
        new DependencyListPlugin(),

        // Automatically require used modules
        new webpack.ProvidePlugin({
            jstz: 'jstz',
            Pickr: '@simonwep/pickr',
            saveAs: 'file-saver'
        })

    ],
    resolve: {

        // Include Node modules and base source tree within search path for
        // import/resolve
        modules: [
            'src',
            'node_modules'
        ]

    }

};

