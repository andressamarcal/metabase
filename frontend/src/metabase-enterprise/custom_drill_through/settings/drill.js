import React from "react";

import { t } from "ttag";

import { renderLinkURLForClick } from "metabase/lib/formatting/link";
import { open } from "metabase/lib/dom";

import ChartSettingInputWithInfo from "../components/ChartSettingInputWithInfo";

export const drillThroughSettings = ({
  objectNames = t`something in this chart`,
  getHidden = () => false,
} = {}) => ({
  click: {
    title: t`What should happen when you click on ${objectNames}?`,
    section: t`Drill-through`,
    widget: "radio",
    default: "menu",
    props: {
      options: [
        { name: t`Open the actions menu`, value: "menu" },
        { name: t`Go to a custom link`, value: "link" },
      ],
    },
    getHidden,
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
    getProps: (
      [
        {
          data: { cols },
        },
      ],
      settings,
    ) => ({
      placeholder: t`e.g. http://acme.cool-crm.com/client/{{column}}`,
      infoName: t`Columns`,
      infos: cols.map(col => col.name),
    }),
    getHidden: (series, settings, extra) =>
      getHidden(series, settings, extra) || settings["click"] !== "link",
    readDependencies: ["click"],
  },
});

export function hasLink(clicked, settings) {
  return (
    settings && settings["click"] === "link" && settings["click_link_template"]
  );
}

export function getLink(clicked, settings) {
  return renderLinkURLForClick(settings["click_link_template"] || "", clicked);
}

export function clickLink(clicked, settings) {
  if (hasLink(clicked, settings)) {
    const url = getLink(clicked, settings);
    if (url) {
      open(url);
    }
  }
}
