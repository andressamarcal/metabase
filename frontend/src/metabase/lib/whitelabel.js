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

const originalColors = { ...colors };

const BRAND_NORMAL_COLOR = Color(brand.normal).hsl();
const COLOR_REGEX = /(?:#[a-fA-F0-9]{3}(?:[a-fA-F0-9]{3})?\b|(?:rgb|hsl)a?\(\s*\d+\s*(?:,\s*\d+(?:\.\d+)?%?\s*){2,3}\))/g;

// TODO: replace saturated, desaturated, brand.saturated, brand.desaturated with computed colors
const COLOR_FAMILIES = [colors, brand, normal, saturated, desaturated];

const COLOR_UPDATORS_BY_COLOR_NAME = {};

// a color not found anywhere in the app
const RANDOM_COLOR = Color({ r: 0xab, g: 0xcd, b: 0xed });

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
      console.log("INIT CSS", colorName, cssProperty, cssValue);
      COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(colorScheme => {
        style[cssProperty] = replaceColors(
          cssValue,
          originalColor,
          colorScheme,
        );
      });
    }
  });
}

function initCSSBrandHueUpdator() {
  // initialize the ".brand-hue" CSS rule, which is used to change the hue of images which should
  // only contain the brand color or completely desaturated colors
  const rotateHueRule = addCSSRule(".brand-hue", "filter: hue-rotate(0);");
  COLOR_UPDATORS_BY_COLOR_NAME["brand"].push(colorScheme => {
    const degrees =
      Color(colorScheme)
        .hsl()
        .hue() - BRAND_NORMAL_COLOR.hue();
    rotateHueRule.style["filter"] = `hue-rotate(${degrees}deg)`;
  });
}

function initJSColorUpdators(colorName) {
  const matchColor = Color(originalColors[colorName]);
  for (const family of COLOR_FAMILIES) {
    for (const [name, colorString] of Object.entries(family)) {
      const color = Color(colorString);
      if (color.hex() === matchColor.hex()) {
        console.log("INIT JS", colorName, name);
        COLOR_UPDATORS_BY_COLOR_NAME[colorName].push(colorScheme => {
          family[name] = colorScheme;
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

function updateColor(colorName, colorScheme) {
  for (const colorUpdator of COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    colorUpdator(colorScheme);
  }
}

export function updateColorScheme() {
  const colorScheme = MetabaseSettings.colorScheme();

  // TODO: other colors
  const colorName = "brand";

  if (!COLOR_UPDATORS_BY_COLOR_NAME[colorName]) {
    initColorUpdators(colorName);
  }
  updateColor(colorName, colorScheme);
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
