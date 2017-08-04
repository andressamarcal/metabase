
import MetabaseSettings from "metabase/lib/settings";

import Color from "color";

import { brand } from "metabase/lib/colors";

const BRAND_COLOR = Color(brand).hsl();
const COLOR_REGEX = /rgba?\([^)]+\)/g;

const round = (value, factor = 5) =>
    Math.round(value / factor) * factor;

export const colorForScheme = (scheme, original) =>
    original
        .hue(scheme.hue())
        .saturationl(scheme.saturationl() *
            (1 + (original.saturationl() - BRAND_COLOR.saturationl()) / BRAND_COLOR.saturationl()))
        .lightness(scheme.lightness() *
            (1 + (original.lightness() - BRAND_COLOR.lightness()) / BRAND_COLOR.lightness()))
        .string();

const replaceBrandColors = (value, colorScheme) => {
    const scheme = Color(colorScheme).hsl();

    return value.replace(COLOR_REGEX, (colorOriginal) => {
        const original = Color(colorOriginal).hsl();
        if (round(BRAND_COLOR.hue()) === round(original.hue())) {
            // match the scheme's hue and offset the scheme's saturation and lightness by the same
            // percentage as the original color is offset from the default brand color
            return colorForScheme(scheme, original);
        } else {
            return colorOriginal;
        }
    })
}

const styleColorUpdators = [];
function initStyleColorUpdators() {
    for (const sheet of document.styleSheets) {
        for (const rule of sheet.cssRules || sheet.rules || []) {
            if (COLOR_REGEX.test(rule.cssText) && rule.style) {
                for (const [prop, value] of Object.entries(rule.style)) {
                    // try replacing with a random color to see if we actually need to
                    if (COLOR_REGEX.test(value) && value !== replaceBrandColors(value, "#ABCDEF")) {
                        styleColorUpdators.push(colorScheme => {
                            rule.style[prop] = replaceBrandColors(value, colorScheme);
                        });
                    }
                }
            }
        }
    }
}

export function updateColorScheme() {
    if (styleColorUpdators.length === 0) {
        initStyleColorUpdators();
    }
    const colorScheme = MetabaseSettings.colorScheme();
    for (const colorUpdator of styleColorUpdators) {
        colorUpdator(colorScheme);
    }
}
