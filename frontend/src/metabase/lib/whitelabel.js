import MetabaseSettings from "metabase/lib/settings";

import Color from "color";

import colors, {
  brand,
  normal,
  harmony,
  saturated,
  desaturated,
} from "metabase/lib/colors";
import { addCSSRule } from "metabase/lib/dom";

export const originalColors = { ...colors };

const BRAND_NORMAL_COLOR = Color(brand.normal).hsl();
const COLOR_REGEX = /(?:#[a-fA-F0-9]{3}(?:[a-fA-F0-9]{3})?\b|(?:rgb|hsl)a?\(\s*\d+\s*(?:,\s*\d+(?:\.\d+)?%?\s*){2,3}\))/g;

// TODO: replace saturated, desaturated, brand.saturated, brand.desaturated with computed colors
const COLOR_FAMILIES = [colors, brand, normal, saturated, desaturated];

const COLOR_UPDATORS_BY_COLOR_NAME = {};

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

function initCSSColorUpdators(colorName) {
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
      COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(themeColor => {
        style[cssProperty] = replaceColors(cssValue, originalColor, themeColor);
      });
    }
  });
}

function initCSSBrandHueUpdator() {
  // initialize the ".brand-hue" CSS rule, which is used to change the hue of images which should
  // only contain the brand color or completely desaturated colors
  const rotateHueRule = addCSSRule(".brand-hue", "filter: hue-rotate(0);");
  COLOR_UPDATORS_BY_COLOR_NAME["brand"].push(themeColor => {
    const degrees =
      Color(themeColor)
        .hsl()
        .hue() - BRAND_NORMAL_COLOR.hue();
    rotateHueRule.style["filter"] = `hue-rotate(${degrees}deg)`;
  });
}

function getColorPaths(family, parentPath = []) {
  const paths = [];
  for (const [name, value] of Object.entries(family)) {
    if (value && typeof value === "object") {
      paths.push(...getColorPaths(value, parentPath.concat(name)));
    } else {
      paths.push(parentPath.concat(name));
    }
  }
  return paths;
}
function getColor(family, path) {
  return path.reduce((o, k) => o[k], family);
}
function setColor(family, path, color) {
  let object = family;
  for (let i = 0; i < path.length; i++) {
    if (i < path.length - 1) {
      object = object[path[i]];
    } else {
      object[path[i]] = color;
    }
  }
}

function initJSColorUpdators(colorName) {
  const matchColor = Color(originalColors[colorName]);
  for (const colorFamily of COLOR_FAMILIES) {
    const colorPaths = getColorPaths(colorFamily);
    for (const colorPath of colorPaths) {
      const colorString = getColor(colorFamily, colorPath);
      const color = Color(colorString);
      if (color.hex() === matchColor.hex()) {
        COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(themeColor => {
          setColor(colorFamily, colorPath, themeColor);
        });
      }
    }
  }
}

function initColorUpdators(colorName) {
  COLOR_UPDATORS_BY_COLOR_NAME[colorName] = [];
  if (colorName === "brand") {
    initCSSBrandHueUpdator();
  }
  initCSSColorUpdators(colorName);
  initJSColorUpdators(colorName);
  // TODO: color harmony
}

function updateColor(colorName, themeColor) {
  for (const colorUpdator of COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    colorUpdator(themeColor);
  }
}

export function updateColorScheme() {
  const colorScheme = MetabaseSettings.colorScheme();
  for (const [colorName, themeColor] of Object.entries(colorScheme)) {
    if (!COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
      initColorUpdators(colorName);
    }
    updateColor(colorName, themeColor);
  }
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
