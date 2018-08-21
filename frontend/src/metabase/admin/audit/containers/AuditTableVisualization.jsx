import React from "react";

import { registerVisualization } from "metabase/visualizations/index";

import { formatColumn, formatValue } from "metabase/lib/formatting";

import Table from "metabase/visualizations/visualizations/Table";

import _ from "underscore";

export default class AuditTableVisualization extends React.Component {
  static identifier = "audit-table";
  static noHeader = true;

  // copy Table's settings
  static settings = Table.settings;

  render() {
    const {
      series: [{ data: { cols, rows }, card }],
      visualizationIsClickable,
      onVisualizationClick,
      settings,
    } = this.props;

    const columnSettings = settings["table.columns"];
    const columnIndexes = columnSettings.map(({ name, enabled }) =>
      _.findIndex(cols, col => col.name === name),
    );

    console.group(card.dataset_query.fn);
    console.log("all:", cols.map(col => col.name));
    console.log("current:", settings["table.columns"]);
    console.groupEnd();

    return (
      <table className="ContentTable">
        <thead>
          <tr>
            {columnIndexes.map(colIndex => (
              <th>{formatColumn(cols[colIndex])}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr>
              {columnIndexes.map((colIndex, columnSettingsIndex) => {
                const value = row[colIndex];
                const column = cols[colIndex];
                const clickObject = { column, value, row, cols };
                const clickable = visualizationIsClickable(clickObject);
                const settings = columnSettings[columnSettingsIndex];
                if (settings && !settings.enabled) {
                  return null;
                }
                return (
                  <td
                    className={clickable ? "text-brand cursor-pointer" : null}
                    onClick={
                      clickable ? () => onVisualizationClick(clickObject) : null
                    }
                  >
                    {formatValue(value, { column, ...(settings || {}) })}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
}

registerVisualization(AuditTableVisualization);
