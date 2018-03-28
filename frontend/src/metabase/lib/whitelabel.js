import MetabaseSettings from "metabase/lib/settings";

import Color from "color";

import { brand, harmony } from "metabase/lib/colors";
import { addCSSRule } from "metabase/lib/dom";

const BRAND_NORMAL_COLOR = Color(brand.normal).hsl();
const COLOR_REGEX = /rgba?\([^)]+\)/g;

const BRAND_THRESHOLD = 15;
const DESATURATE_THRESHOLD = 0; // NOTE: disabled for now, increased BRAND_THRESHOLD instead

const hasSimilarHue = (colorA, colorB, threshold) =>
  Math.abs(colorA.hue() - colorB.hue()) < threshold;

export const colorForScheme = (scheme, original) =>
  original
    .hue(scheme.hue())
    .saturationl(
      scheme.saturationl() *
        (1 +
          (original.saturationl() - BRAND_NORMAL_COLOR.saturationl()) /
            BRAND_NORMAL_COLOR.saturationl()),
    )
    .lightness(
      scheme.lightness() *
        (1 +
          (original.lightness() - BRAND_NORMAL_COLOR.lightness()) /
            BRAND_NORMAL_COLOR.lightness()),
    )
    .string();

const replaceBrandColors = (value, colorScheme) => {
  const scheme = Color(colorScheme).hsl();

  return value.replace(COLOR_REGEX, colorOriginal => {
    const original = Color(colorOriginal).hsl();
    if (hasSimilarHue(BRAND_NORMAL_COLOR, original, BRAND_THRESHOLD)) {
      // match the scheme's hue and offset the scheme's saturation and lightness by the same
      // percentage as the original color is offset from the default brand color
      return colorForScheme(scheme, original);
    } else if (
      hasSimilarHue(BRAND_NORMAL_COLOR, original, DESATURATE_THRESHOLD)
    ) {
      // TODO: also check that the color is sufficiently desaturated or lightened?
      return original.saturationl(0).string();
    } else {
      return colorOriginal;
    }
  });
};

const STYLE_UPDATORS = [];
const STYLE_RESETERS = [];

function initBrandHueUpdator() {
  // initialize the ".brand-hue" CSS rule, which is used to change the hue of images which should
  // only contain the brand color or completely desaturated colors
  const rotateHueRule = addCSSRule(".brand-hue", "filter: hue-rotate(0);");
  STYLE_UPDATORS.push(colorScheme => {
    const degrees =
      Color(colorScheme)
        .hsl()
        .hue() - BRAND_NORMAL_COLOR.hue();
    rotateHueRule.style["filter"] = `hue-rotate(${degrees}deg)`;
  });
  STYLE_RESETERS.push(() => {
    rotateHueRule.style["filter"] = `hue-rotate(0)`;
  });
}

function walkStyleSheets(sheets, fn) {
  for (const sheet of sheets) {
    for (const rule of sheet.cssRules || sheet.rules || []) {
      if (rule.cssRules) {
        // child sheets, e.x. media queries
        walkStyleSheets([rule], fn);
      }
      for (const prop in rule.style || {}) {
        fn(rule.style, prop);
      }
    }
  }
}

function initBrandColorUpdators() {
  // look for CSS rules which have colors matching the brand colors or very light or desaturated
  walkStyleSheets(document.styleSheets, (style, prop) => {
    const value = style[prop];
    // try replacing with a random color to see if we actually need to
    if (
      COLOR_REGEX.test(value) &&
      value !== replaceBrandColors(value, "#ABCDEF")
    ) {
      STYLE_UPDATORS.push(colorScheme => {
        style[prop] = replaceBrandColors(value, colorScheme);
      });
      STYLE_RESETERS.push(() => {
        style[prop] = value;
      });
    }
  });
}

// colors.css, etc: CSS rules
function updateCSSRules(colorScheme) {
  if (STYLE_UPDATORS.length === 0) {
    initBrandHueUpdator();
    initBrandColorUpdators();
  }
  for (const styleUpdator of STYLE_UPDATORS) {
    styleUpdator(colorScheme);
  }
}

function resetCSSRules() {
  for (const styleReseter of STYLE_RESETERS) {
    styleReseter();
  }
}

// colors.js: brand colors
const BRAND_ORIGINAL = { ...brand };
function updateBrandColors(colorScheme) {
  for (const name in brand) {
    brand[name] = colorForScheme(
      Color(colorScheme).hsl(),
      Color(BRAND_ORIGINAL[name]).hsl(),
    );
  }
}

function resetBrandColors() {
  for (const name in brand) {
    brand[name] = BRAND_ORIGINAL[name];
  }
}

// colors.js: color harmony
const HARMONY_ORIGINAL = [...harmony];
function updateHarmoneyColors(colorScheme) {
  // find the "closest" color in the color harmony
  // TODO: account for saturation and lightness?
  const closest = findClosestIndex(
    HARMONY_ORIGINAL.map(c =>
      Color(c)
        .hsl()
        .hue(),
    ),
    Color(colorScheme)
      .hsl()
      .hue(),
  );
  // replace that color, and rotate the harmony such that it's first so that it's the default
  // color for charts, etc
  const newHarmony = [
    colorScheme,
    ...HARMONY_ORIGINAL.slice(closest),
    ...HARMONY_ORIGINAL.slice(0, closest),
  ];
  // mutate the existing array since that export is used directly in many places ¯\_(ツ)_/¯
  harmony.splice(0, harmony.length, ...newHarmony);
}

function resetHarmoneyColors() {
  harmony.splice(0, harmony.length, ...HARMONY_ORIGINAL);
}

function findClosestIndex(array, value) {
  let minDelta = Infinity;
  let minIndex = -1;
  for (let i = 0; i < array.length; i++) {
    const delta = Math.abs(value - array[i]);
    if (delta < minDelta) {
      minDelta = delta;
      minIndex = i;
    }
  }
  return minIndex;
}

function isDefaultBrandColor(colorScheme) {
  return colorScheme === BRAND_ORIGINAL.normal;
}

export function updateColorScheme() {
  const colorScheme = MetabaseSettings.colorScheme();
  if (!isDefaultBrandColor(colorScheme)) {
    updateCSSRules(colorScheme);
    updateBrandColors(colorScheme);
    updateHarmoneyColors(colorScheme);
  } else {
    resetCSSRules();
    resetBrandColors();
    resetHarmoneyColors();
  }
}
