import MetabaseSettings from "metabase/lib/settings";

import Color from "color";

import colors, { syncColors } from "metabase/lib/colors";
import { addCSSRule } from "metabase/lib/dom";

export const originalColors = { ...colors };

console.log(colors.brand);

const BRAND_NORMAL_COLOR = Color(colors.brand).hsl();
const COLOR_REGEX = /(?:#[a-fA-F0-9]{3}(?:[a-fA-F0-9]{3})?\b|(?:rgb|hsl)a?\(\s*\d+\s*(?:,\s*\d+(?:\.\d+)?%?\s*){2,3}\))/g;

const CSS_COLOR_UPDATORS_BY_COLOR_NAME = {};
const JS_COLOR_UPDATORS_BY_COLOR_NAME = {};

// a color not found anywhere in the app
const RANDOM_COLOR = Color({ r: 0xab, g: 0xcd, b: 0xed });

function walkStyleSheets(sheets, fn) {
  for (const sheet of sheets) {
    let rules = [];
    try {
      // try/catch due to CORS being enforced in Chrome
      rules = sheet.cssRules || sheet.rules || [];
    } catch (e) {}
    for (const rule of rules) {
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

const replaceColors = (cssValue, matchColor, replacementColor) => {
  return cssValue.replace(COLOR_REGEX, colorString => {
    const color = Color(colorString);
    if (color.hex() === matchColor.hex()) {
      if (color.alpha() < 1) {
        return Color(replacementColor)
          .alpha(color.alpha())
          .string();
      } else {
        return replacementColor;
      }
    }
    return colorString;
  });
};

function initColorCSS(colorName) {
  if (CSS_COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    return;
  }
  CSS_COLOR_UPDATORS_BY_COLOR_NAME[colorName] = [];

  // special updator for brand
  if (colorName === "brand") {
    initCSSBrandHueUpdator();
  }

  const originalColor = Color(originalColors[colorName]);
  // look for CSS rules which have colors matching the brand colors or very light or desaturated
  walkStyleSheets(document.styleSheets, (style, cssProperty) => {
    // save the original value here so we have a copy to perform the replacement on
    const cssValue = style[cssProperty];
    if (
      // don't bother with checking if there are no colors
      COLOR_REGEX.test(cssValue) &&
      // try replacing with a random color to see if we actually need to
      cssValue !== replaceColors(cssValue, originalColor, RANDOM_COLOR)
    ) {
      CSS_COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(themeColor => {
        style[cssProperty] = replaceColors(cssValue, originalColor, themeColor);
      });
    }
  });
}

function initCSSBrandHueUpdator() {
  // initialize the ".brand-hue" CSS rule, which is used to change the hue of images which should
  // only contain the brand color or completely desaturated colors
  const rotateHueRule = addCSSRule(".brand-hue", "filter: hue-rotate(0);");
  CSS_COLOR_UPDATORS_BY_COLOR_NAME["brand"].push(themeColor => {
    const degrees =
      Color(themeColor)
        .hsl()
        .hue() - BRAND_NORMAL_COLOR.hue();
    rotateHueRule.style["filter"] = `hue-rotate(${degrees}deg)`;
  });
}

function initColorJS(colorName) {
  if (JS_COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    return;
  }
  JS_COLOR_UPDATORS_BY_COLOR_NAME[colorName] = [];
  JS_COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(themeColor => {
    colors[colorName] = themeColor;
  });
}

function updateColorJS(colorName, themeColor) {
  initColorJS(colorName);
  for (const colorUpdator of JS_COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    colorUpdator(themeColor);
  }
  syncColors();
}

function updateColorCSS(colorName, themeColor) {
  initColorCSS(colorName);
  for (const colorUpdator of CSS_COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    colorUpdator(themeColor);
  }
}

export function updateColorsJS() {
  const colorScheme = MetabaseSettings.colorScheme();
  for (const [colorName, themeColor] of Object.entries(colorScheme)) {
    updateColorJS(colorName, themeColor);
  }
}

export function updateColorsCSS() {
  const colorScheme = MetabaseSettings.colorScheme();
  for (const [colorName, themeColor] of Object.entries(colorScheme)) {
    updateColorCSS(colorName, themeColor);
  }
}

export function updateColors() {
  updateColorsCSS();
  updateColorsJS();
}

// APPLICATION NAME

function replaceApplicationName(string) {
  return string.replace(/Metabase/g, MetabaseSettings.applicationName());
}

export function enabledApplicationNameReplacement() {
  const c3po = require("c-3po");
  const _t = c3po.t;
  const _jt = c3po.jt;
  const _ngettext = c3po.ngettext;
  c3po.t = (...args) => {
    return replaceApplicationName(_t(...args));
  };
  c3po.ngettext = (...args) => {
    return replaceApplicationName(_ngettext(...args));
  };
  c3po.jt = (...args) => {
    return _jt(...args).map(
      element =>
        typeof element === "string" ? replaceApplicationName(element) : element,
    );
  };
}

enabledApplicationNameReplacement();
