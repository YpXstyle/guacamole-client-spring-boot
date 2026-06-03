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

/**
 * Color calculation engine for the Guacamole theme system.
 *
 * Takes a primary brand color and theme mode (light/dark) as input,
 * and deterministically calculates all 35 CSS variables needed for
 * the application theme.
 *
 * Registered as an AngularJS factory for dependency injection.
 */
angular.module('index').factory('colorEngine', [function() {

    'use strict';

    /**
     * Converts a hex color string to RGB values.
     *
     * @param {string} hex - The hex color string (e.g., "#FF0000")
     * @returns {object} An object with r, g, b properties (0-255)
     */
    function hexToRgb(hex) {
        // Remove # prefix
        hex = hex.replace(/^#/, '');

        // Handle 3-digit hex
        if (hex.length === 3) {
            hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
        }

        var r = parseInt(hex.substring(0, 2), 16);
        var g = parseInt(hex.substring(2, 4), 16);
        var b = parseInt(hex.substring(4, 6), 16);

        return { r: r, g: g, b: b };
    }

    /**
     * Converts RGB values to a hex color string.
     *
     * @param {number} r - Red component (0-255)
     * @param {number} g - Green component (0-255)
     * @param {number} b - Blue component (0-255)
     * @returns {string} The hex color string (e.g., "#FF0000")
     */
    function rgbToHex(r, g, b) {
        r = Math.max(0, Math.min(255, Math.round(r)));
        g = Math.max(0, Math.min(255, Math.round(g)));
        b = Math.max(0, Math.min(255, Math.round(b)));

        return '#' + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1).toUpperCase();
    }

    /**
     * Converts RGB values to HSL.
     *
     * @param {number} r - Red component (0-255)
     * @param {number} g - Green component (0-255)
     * @param {number} b - Blue component (0-255)
     * @returns {object} An object with h (0-360), s (0-1), l (0-1) properties
     */
    function rgbToHsl(r, g, b) {
        r /= 255;
        g /= 255;
        b /= 255;

        var max = Math.max(r, g, b);
        var min = Math.min(r, g, b);
        var h, s, l = (max + min) / 2;

        if (max === min) {
            h = s = 0;
        } else {
            var d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);

            switch (max) {
                case r:
                    h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
                    break;
                case g:
                    h = ((b - r) / d + 2) / 6;
                    break;
                case b:
                    h = ((r - g) / d + 4) / 6;
                    break;
            }
        }

        return { h: h * 360, s: s, l: l };
    }

    /**
     * Converts HSL values to RGB.
     *
     * @param {number} h - Hue (0-360)
     * @param {number} s - Saturation (0-1)
     * @param {number} l - Lightness (0-1)
     * @returns {object} An object with r, g, b properties (0-255)
     */
    function hslToRgb(h, s, l) {
        var r, g, b;

        if (s === 0) {
            r = g = b = l;
        } else {
            function hue2rgb(p, q, t) {
                if (t < 0) t += 1;
                if (t > 1) t -= 1;
                if (t < 1 / 6) return p + (q - p) * 6 * t;
                if (t < 1 / 2) return q;
                if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
                return p;
            }

            var q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            var p = 2 * l - q;

            r = hue2rgb(p, q, h / 360 + 1 / 3);
            g = hue2rgb(p, q, h / 360);
            b = hue2rgb(p, q, h / 360 - 1 / 3);
        }

        return {
            r: Math.round(r * 255),
            g: Math.round(g * 255),
            b: Math.round(b * 255)
        };
    }

    /**
     * Converts a hex color to HSL.
     *
     * @param {string} hex - The hex color string
     * @returns {object} An object with h, s, l properties
     */
    function hexToHsl(hex) {
        var rgb = hexToRgb(hex);
        return rgbToHsl(rgb.r, rgb.g, rgb.b);
    }

    /**
     * Converts HSL to hex color.
     *
     * @param {object} hsl - An object with h, s, l properties
     * @returns {string} The hex color string
     */
    function hslToHex(hsl) {
        var rgb = hslToRgb(hsl.h, hsl.s, hsl.l);
        return rgbToHex(rgb.r, rgb.g, rgb.b);
    }

    /**
     * Darkens a color by the given percentage.
     *
     * @param {string} hex - The hex color string
     * @param {number} percent - The percentage to darken (0-100)
     * @returns {string} The darkened hex color
     */
    function darken(hex, percent) {
        var hsl = hexToHsl(hex);
        hsl.l = Math.max(0, hsl.l - percent / 100);
        return hslToHex(hsl);
    }

    /**
     * Lightens a color by the given percentage.
     *
     * @param {string} hex - The hex color string
     * @param {number} percent - The percentage to lighten (0-100)
     * @returns {string} The lightened hex color
     */
    function lighten(hex, percent) {
        var hsl = hexToHsl(hex);
        hsl.l = Math.min(1, hsl.l + percent / 100);
        return hslToHex(hsl);
    }

    /**
     * Calculates the contrast color (black or white) for text on a
     * given background color, following WCAG AA standards.
     *
     * @param {string} hex - The background hex color
     * @returns {string} "#000000" or "#FFFFFF" for optimal contrast
     */
    function contrastColor(hex) {
        var rgb = hexToRgb(hex);
        // Relative luminance calculation (WCAG 2.1)
        var luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b) / 255;
        return luminance > 0.5 ? '#000000' : '#FFFFFF';
    }

    /**
     * Blends two colors with the given opacity.
     *
     * @param {string} foreground - The foreground hex color
     * @param {string} background - The background hex color
     * @param {number} opacity - The foreground opacity (0-1)
     * @returns {string} The blended hex color
     */
    function blend(foreground, background, opacity) {
        var fg = hexToRgb(foreground);
        var bg = hexToRgb(background);

        var r = Math.round(fg.r * opacity + bg.r * (1 - opacity));
        var g = Math.round(fg.g * opacity + bg.g * (1 - opacity));
        var b = Math.round(fg.b * opacity + bg.b * (1 - opacity));

        return rgbToHex(r, g, b);
    }

    /**
     * Calculates all CSS variables for the given theme configuration.
     *
     * @param {object} themeConfig - Theme configuration object
     * @param {string} themeConfig.primaryColor - Brand primary color (#RRGGBB)
     * @param {string} [themeConfig.accentColor] - Brand accent color
     * @param {string} [themeConfig.successColor] - Success state color
     * @param {string} [themeConfig.warningColor] - Warning state color
     * @param {string} [themeConfig.dangerColor] - Danger state color
     * @param {string} mode - "light" or "dark"
     * @returns {object} Map of CSS variable names to values
     */
    function calculate(themeConfig, mode) {
        var primary = themeConfig.primaryColor || '#1a56db';
        var accent = themeConfig.accentColor || '#0694a2';
        var isDark = (mode === 'dark') ||
            (mode === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches);

        return {
            // Primary color system
            '--gc-color-primary': primary,
            '--gc-color-primary-hover': darken(primary, 15),
            '--gc-color-primary-active': darken(primary, 25),
            '--gc-color-text-on-primary': contrastColor(primary),
            '--gc-color-accent': accent,

            // Semantic colors
            '--gc-color-success': themeConfig.successColor || '#A3D655',
            '--gc-color-warning': themeConfig.warningColor || '#f4b400',
            '--gc-color-warning-border': '#FA0',
            '--gc-color-danger': themeConfig.dangerColor || '#A43',
            '--gc-color-danger-hover': darken(themeConfig.dangerColor || '#A43', 15),
            '--gc-color-danger-active': darken(themeConfig.dangerColor || '#A43', 25),
            '--gc-color-info': isDark ? '#6db3f2' : accent,

            // Neutral colors (mode-dependent)
            '--gc-color-bg': isDark ? '#1a1a1a' : '#ffffff',
            '--gc-color-bg-secondary': isDark ? '#2d2d2d' : '#EEEEEE',
            '--gc-color-bg-subtle': isDark ? '#252525' : '#F5F5F5',
            '--gc-color-text': isDark ? '#e0e0e0' : '#000000',
            '--gc-color-text-secondary': isDark ? '#a0a0a0' : '#808080',
            '--gc-color-text-muted': isDark ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)',
            '--gc-color-text-inverse': isDark ? '#000000' : '#ffffff',
            '--gc-color-border': isDark ? '#666666' : '#BBBBBB',
            '--gc-color-border-light': isDark ? '#555555' : '#AAAAAA',
            '--gc-color-border-input': isDark ? '#777777' : '#777777',
            '--gc-color-link': isDark ? '#6db3f2' : '#0000ee',

            // Functional colors (derived from primary + mode)
            '--gc-color-selected': isDark ? blend(primary, '#1a1a1a', 0.2) : blend(primary, '#ffffff', 0.15),
            '--gc-color-hover': isDark ? blend(primary, '#1a1a1a', 0.15) : blend(primary, '#ffffff', 0.1),
            '--gc-color-error-bg': isDark ? blend('#db4437', '#1a1a1a', 0.2) : '#FDD',
            '--gc-color-error-text': isDark ? '#ff6b6b' : '#964040',
            '--gc-color-overlay': isDark ? 'rgba(0,0,0,0.7)' : 'rgba(0,0,0,0.5)',

            // Shadows
            '--gc-color-shadow': isDark ? 'rgba(0,0,0,0.4)' : 'rgba(0,0,0,0.125)',
            '--gc-color-shadow-strong': isDark ? 'rgba(0,0,0,0.6)' : 'rgba(0,0,0,0.25)',

            // Login page
            '--gc-login-bg': isDark ? '#1a1a1a' : '#ffffff'
        };
    }

    // Public API
    return {
        calculate: calculate,
        darken: darken,
        lighten: lighten,
        contrastColor: contrastColor,
        blend: blend,
        hexToRgb: hexToRgb,
        rgbToHex: rgbToHex,
        hexToHsl: hexToHsl,
        hslToHex: hslToHex
    };

}]);
