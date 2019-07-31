import { isDate } from "metabase/lib/schema_metadata";

import { formatValue } from "metabase/lib/formatting";
import { parseTimestamp } from "metabase/lib/time";

function formatValueForLinkTemplate(value, column) {
  if (isDate(column) && column.unit) {
    return formatDateTimeForParameter(value, column.unit);
  }
  return value;
}

export function renderLinkTextForClick(template, clicked) {
  return renderTemplateForClick(template, clicked, ({ value, column }) =>
    formatValue(value, { column }),
  );
}

export function renderLinkURLForClick(template, clicked) {
  return renderTemplateForClick(template, clicked, ({ value, column }) =>
    encodeURIComponent(formatValueForLinkTemplate(value, column)),
  );
}

export function renderTemplateForClick(
  template,
  clicked,
  formatFunction = ({ value }) => value,
) {
  return template.replace(/{{([^}]+)}}/g, (whole, columnName) => {
    const valueAndColumn = getValueAndColumnForColumnName(clicked, columnName);
    if (valueAndColumn) {
      return formatFunction(valueAndColumn);
    }
    console.warn("Missing value for " + name);
    return "";
  });
}

function getValueAndColumnForColumnName(clicked, columnName) {
  const name = columnName.toLowerCase();
  if (clicked.origin) {
    const { cols } = clicked.origin;
    // optimization for origin since it's used in tables
    if (clicked.origin._columnIndex == null) {
      const index = (clicked.origin._columnIndex = {});
      for (let i = 0; i < cols.length; i++) {
        index[cols[i].name.toLowerCase()] = i;
      }
    }
    const index = clicked.origin._columnIndex[name];
    if (index != null) {
      return { value: clicked.origin.row[index], column: cols[index] };
    }
  }
  for (const { value, column } of [clicked, ...(clicked.dimensions || [])]) {
    if (column && column.name.toLowerCase() === name) {
      return { value, column };
    }
  }
}

export function formatDateTimeForParameter(value, unit) {
  let m = parseTimestamp(value, unit);
  if (!m.isValid()) {
    return String(value);
  }

  if (unit === "month") {
    return m.format("YYYY-MM");
  } else if (unit === "quarter") {
    return m.format("[Q]Q-YYYY");
  } else if (unit === "date") {
    return m.format("YYYY-MM-DD");
  } else if (unit) {
    const start = m.clone().startOf(unit);
    const end = m.clone().endOf(unit);
    if (start.isValid() && end.isValid()) {
      return `${start.format("YYYY-MM-DD")}~${end.format("YYYY-MM-DD")}`;
    }
    return String(value);
  }
}
