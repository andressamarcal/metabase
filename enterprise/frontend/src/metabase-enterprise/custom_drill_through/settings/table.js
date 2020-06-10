import React from "react";
import { t } from "ttag";

import { conjunct } from "metabase/lib/formatting";

import {
  isString,
  isURL,
  isEmail,
  isImageURL,
  isAvatarURL,
} from "metabase/lib/schema_metadata";

import ChartSettingInputWithInfo from "../components/ChartSettingInputWithInfo";

export function getColumnSettings(column) {
  const settings = {};

  // VIEW AS settings:
  let defaultValue = null;
  const optionNames = [];

  const options: { name: string, value: null | string }[] = [
    { name: t`Off`, value: null },
  ];

  // if (!column.special_type || isURL(column)) {
  if (isURL(column)) {
    defaultValue = "link";
  }
  options.push({ name: t`Link`, value: "link" });
  optionNames.push(t`link`);
  // }

  if (isString(column)) {
    if (!column.special_type || isEmail(column)) {
      if (isEmail(column)) {
        defaultValue = "email_link";
      }
      options.push({ name: t`Email link`, value: "email_link" });
      optionNames.push(t`email`);
    }
    if (!column.special_type || isImageURL(column) || isAvatarURL(column)) {
      if (isImageURL(column) || isAvatarURL(column)) {
        defaultValue = isAvatarURL(column) ? "image" : "link";
      }
      options.push({ name: t`Image`, value: "image" });
      optionNames.push(t`image`);
    }
    if (!column.special_type) {
      defaultValue = "auto";
      options.push({ name: t`Automatic`, value: "auto" });
    }
  }

  if (options.length > 1) {
    settings["view_as"] = {
      title: t`Display as ${conjunct(optionNames, t`or`)}`,
      widget: "select",
      default: defaultValue,
      props: {
        options,
      },
    };
  }

  settings["link_template"] = {
    title: t`Link template`,
    description: (
      <span>
        {t`The full URL for where this link should go. You can use the name of any column in your question's result to insert its value, like this:`}{" "}
        <strong>{`{{column}}`}</strong>
      </span>
    ),
    widget: ChartSettingInputWithInfo,
    getProps: (
      column,
      settings,
      onChange,
      {
        series: [
          {
            data: { cols },
          },
        ],
      },
    ) => ({
      placeholder: t`e.g. http://acme.cool-crm.com/client/{{column}}`,
      infoName: t`Columns`,
      infos: cols.map(col => col.name),
    }),
    getHidden: (column, settings) => settings["view_as"] !== "link",
    readDependencies: ["view_as"],
  };

  settings["link_text"] = {
    title: t`Link text (optional)`,
    description: (
      <span>
        {t`Whatever you type here will be displayed instead of the value of each cell. You can use the`}{" "}
        <strong>{`{{column}}`}</strong> {t`notation here as well.`}
      </span>
    ),
    widget: "input",
    default: null,
    getHidden: (column, settings) =>
      settings["view_as"] !== "link" && settings["view_as"] !== "email_link",
  };

  return settings;
}
