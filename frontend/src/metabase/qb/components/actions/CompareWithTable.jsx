/* @flow */

import type {
  ClickAction,
  ClickActionProps,
} from "metabase/meta/types/Visualization";

import { t } from "c-3po";
import { utf8_to_b64url } from "metabase/lib/card";
import StructuredQuery from "metabase-lib/lib/queries/StructuredQuery";

export default ({ question }: ClickActionProps): ClickAction[] => {
  const query = question.query();
  if (
    !(query instanceof StructuredQuery) ||
    !query.isBareRows() ||
    query.filters().length == 0
  ) {
    return [];
  }
  const tableId = question.query().tableId();
  return [
    {
      name: "xray-card",
      title: t`Compare this with all rows in the table`,
      icon: "beaker",

      url: () =>
        question.card().id
          ? `/api/automagic-dashboards/table/${tableId}/compare/question/${
              question.card().id
            }`
          : `/api/automagic-dashboards/table/${tableId}/compare/adhoc/${utf8_to_b64url(
              JSON.stringify(question.card().dataset_query),
            )}`,
    },
  ];
};
