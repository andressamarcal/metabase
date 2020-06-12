import { PLUGIN_FORMATTING_HELPERS } from "metabase/plugins";
import { formatValue, getRemappedValue } from "metabase/lib/formatting";

import { renderLinkURLForClick, renderLinkTextForClick } from "./link";

PLUGIN_FORMATTING_HELPERS.url = (value, { link_template, clicked }) =>
  link_template && clicked
    ? renderLinkURLForClick(link_template, clicked)
    : value;

PLUGIN_FORMATTING_HELPERS.urlText = (value, options) => {
  const { link_text, clicked } = options;
  if (link_text && clicked) {
    return renderLinkTextForClick(link_text, clicked);
  }

  return (
    link_text ||
    getRemappedValue(value, options) ||
    formatValue(value, { ...options, view_as: null })
  );
};
