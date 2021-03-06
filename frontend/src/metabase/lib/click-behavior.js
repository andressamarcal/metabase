import _ from "underscore";
import { getIn } from "icepick";
import { t, ngettext, msgid } from "ttag";

import Question from "metabase-lib/lib/Question";
import { TemplateTagVariable } from "metabase-lib/lib/Variable";
import { TemplateTagDimension } from "metabase-lib/lib/Dimension";
import { isa, TYPE } from "metabase/lib/types";
import {
  dimensionFilterForParameter,
  variableFilterForParameter,
} from "metabase/meta/Dashboard";

export function getDataFromClicked({
  extraData: { dashboard, parameterValuesBySlug, userAttributes } = {},
  dimensions,
  data,
}) {
  const column = [
    ...(dimensions || []),
    ...(data || []).map(d => ({ column: d.col, value: d.value })),
  ]
    .filter(d => d.column != null)
    .reduce(
      (acc, { column, value }) =>
        acc[name] === undefined
          ? { ...acc, [column.name.toLowerCase()]: { value, column } }
          : acc,
      {},
    );

  const parameterByName =
    dashboard == null
      ? {}
      : _.chain(dashboard.parameters)
          .filter(p => parameterValuesBySlug[p.slug] != null)
          .map(p => [
            p.name.toLowerCase(),
            { value: parameterValuesBySlug[p.slug] },
          ])
          .object()
          .value();

  const parameterBySlug = _.mapObject(parameterValuesBySlug, value => ({
    value,
  }));

  const parameter =
    dashboard == null
      ? {}
      : _.chain(dashboard.parameters)
          .filter(p => parameterValuesBySlug[p.slug] != null)
          .map(p => [p.id, { value: parameterValuesBySlug[p.slug] }])
          .object()
          .value();

  const userAttribute = _.mapObject(userAttributes, value => ({ value }));

  return { column, parameter, parameterByName, parameterBySlug, userAttribute };
}

const { Text, Number, Temporal } = TYPE;

function notRelativeDateOrRange({ type }) {
  return type !== "date/range" && type !== "date/relative";
}

export function getTargetsWithSourceFilters({ isDash, object, metadata }) {
  if (!isDash) {
    const query = new Question(object, metadata).query();
    return query
      .dimensionOptions()
      .all()
      .concat(query.variables())
      .map(o => {
        let id, target;
        if (
          o instanceof TemplateTagVariable ||
          o instanceof TemplateTagDimension
        ) {
          ({ id, name: target } = o.tag());
        } else {
          target = ["dimension", o.mbql()];
          id = JSON.stringify(target);
        }
        let parentType;
        let parameterSourceFilter = () => true;
        const columnSourceFilter = c => isa(c.base_type, parentType);
        if (o instanceof TemplateTagVariable) {
          parentType = { text: Text, number: Number, date: Temporal }[
            o.tag().type
          ];
          parameterSourceFilter = parameter =>
            variableFilterForParameter(parameter)(o);
        } else if (o.field() != null) {
          const { base_type } = o.field();
          parentType =
            [Temporal, Number, Text].find(t => isa(base_type, t)) || base_type;
          parameterSourceFilter = parameter =>
            dimensionFilterForParameter(parameter)(o);
        }

        return {
          id,
          target,
          name: o.displayName({ includeTable: true }),
          sourceFilters: {
            column: columnSourceFilter,
            parameter: parameterSourceFilter,
            userAttribute: () => parentType === Text,
          },
        };
      });
  } else {
    return object.parameters.map(parameter => {
      const filter = baseTypeFilterForParameterType(parameter.type);
      return {
        id: parameter.id,
        name: parameter.name,
        target: parameter.slug,
        sourceFilters: {
          column: c => notRelativeDateOrRange(parameter) && filter(c.base_type),
          parameter: sourceParam =>
            parameter.type === sourceParam.type &&
            parameter.id !== sourceParam.id,
          userAttribute: () => !parameter.type.startsWith("date"),
        },
      };
    });
  }
}

function baseTypeFilterForParameterType(parameterType) {
  const [typePrefix] = parameterType.split("/");
  const allowedType = {
    date: TYPE.Temporal,
    id: TYPE.Integer,
    category: TYPE.Text,
    location: TYPE.Text,
  }[typePrefix];
  if (allowedType === undefined) {
    // default to showing everything
    return () => true;
  }
  return baseType => isa(baseType, allowedType);
}

export function getClickBehaviorDescription(dashcard) {
  const noBehaviorMessage = hasActionsMenu(dashcard)
    ? t`Open the action menu`
    : t`Do nothing`;
  if (isTableDisplay(dashcard)) {
    const count = Object.values(
      getIn(dashcard, ["visualization_settings", "column_settings"]) || {},
    ).filter(settings => settings.click_behavior != null).length;
    if (count === 0) {
      return noBehaviorMessage;
    }
    return ngettext(
      msgid`${count} column has custom behavior`,
      `${count} columns have custom behavior`,
      count,
    );
  }
  const { click_behavior: clickBehavior } = dashcard.visualization_settings;
  if (clickBehavior == null) {
    return noBehaviorMessage;
  }
  if (clickBehavior.type === "link") {
    const { linkType } = clickBehavior;
    return linkType == null
      ? t`Go to...`
      : linkType === "dashboard"
      ? t`Go to dashboard`
      : linkType === "question"
      ? t`Go to question`
      : t`Go to url`;
  }

  return t`Filter this dashboard`;
}

export function clickBehaviorIsValid(clickBehavior) {
  // opens action menu
  if (clickBehavior == null) {
    return true;
  }
  const {
    type,
    parameterMapping,
    linkType,
    targetId,
    linkTemplate,
  } = clickBehavior;
  if (type === "crossfilter") {
    return Object.keys(parameterMapping).length > 0;
  }
  // if it's not a crossfilter, it's a link
  if (linkType === "url") {
    return (linkTemplate || "").length > 0;
  }
  // if we're linking to a question or dashboard we just need a targetId
  if (linkType === "dashboard" || linkType === "question") {
    return targetId != null;
  }
  // we've picked "link" without picking a link type
  return false;
}

export function hasActionsMenu(dashcard) {
  // This seems to work, but it isn't the right logic.
  // The right thing to do would be to check for any drills. However, we'd need a "clicked" object for that.
  return getIn(dashcard, ["card", "dataset_query", "type"]) !== "native";
}

export function isTableDisplay(dashcard) {
  return dashcard.card.display === "table";
}
