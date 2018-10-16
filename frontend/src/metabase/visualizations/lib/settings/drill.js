import React from "react";

import { t } from "c-3po";

import ChartSettingInputWithInfo from "metabase/visualizations/components/settings/ChartSettingInputWithInfo";

import { renderLinkURLForClick } from "metabase/lib/formatting/link";
import { open } from "metabase/lib/dom";

export const DRILL_THROUGH_SETTINGS = {
  click: {
    title: t`What should happen when you click on a point or bar in this chart?`,
    section: t`Drill-through`,
    widget: "radio",
    default: "menu",
    props: {
      options: [
        { name: t`Open the actions menu`, value: "menu" },
        { name: t`Go to a custom link`, value: "link" },
      ],
    },
  },
  click_link_template: {
    title: t`Link template`,
    section: t`Drill-through`,
    description: (
      <span>
        {t`The full URL for where this link should go. You can use the name of any column in your question's result to insert its value, like this:`}{" "}
        <strong>{`{{column}}`}</strong>
      </span>
    ),
    widget: ChartSettingInputWithInfo,
    getProps: ([{ data: { cols } }], settings) => ({
      placeholder: t`e.g. http://acme.cool-crm.com/client/{{column}}`,
      infoName: t`Columns`,
      infos: cols.map(col => col.name),
    }),
    getHidden: (series, settings) => settings["click"] !== "link",
    readDependencies: ["click"],
  },
};

export function clickLink(clicked, settings) {
  const urlTemplate = settings["click_link_template"];
  if (urlTemplate) {
    const url = renderLinkURLForClick(urlTemplate, clicked);
    if (url) {
      open(url);
    }
  }
}

export function hasLink(clicked, settings) {
  return settings["click"] === "link";
}
