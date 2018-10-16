/* @flow */

import { hasLink, getLink } from "metabase/visualizations/lib/settings/drill";

import { t } from "c-3po";
import type {
  ClickAction,
  ClickActionProps,
} from "metabase/meta/types/Visualization";

export default ({ question, clicked }: ClickActionProps): ClickAction[] => {
  if (!clicked || !clicked.settings || !hasLink(clicked, clicked.settings)) {
    return [];
  }

  return [
    {
      name: "link",
      title: t`Go to link`,
      defaultAlways: true,
      url: () => getLink(clicked, clicked.settings),
    },
  ];
};
