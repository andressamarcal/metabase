/* @flow */

import React from "react";

import { registerVisualization } from "metabase/visualizations/index";

import { formatColumn, formatValue } from "metabase/lib/formatting";
import { isColumnRightAligned } from "metabase/visualizations/lib/table";

import Table from "metabase/visualizations/visualizations/Table";

import EmptyState from "metabase/components/EmptyState";

import { t } from "ttag";

import _ from "underscore";
import cx from "classnames";

export default class AuditTableVisualization extends React.Component {
  static identifier = "audit-table";
  static noHeader = true;

  // copy Table's settings
  static settings = Table.settings;

  render() {
    const {
      series: [
        {
          data: { cols, rows },
        },
      ],
      visualizationIsClickable,
      onVisualizationClick,
      settings,
    } = this.props;

    const columnSettings = settings["table.columns"];
    const columnIndexes = columnSettings.map(({ name, enabled }) =>
      _.findIndex(cols, col => col.name === name),
    );

    if (rows.length === 0) {
      return (
        <EmptyState
          title={t`No results`}
          illustrationElement={<img src="../app/assets/img/no_results.svg" />}
        />
      );
    }

    return (
      <table className="ContentTable">
        <thead>
          <tr>
            {columnIndexes.map(colIndex => (
              <th
                className={cx({
                  "text-right": isColumnRightAligned(cols[colIndex]),
                })}
              >
                {formatColumn(cols[colIndex])}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr>
              {columnIndexes.map((colIndex, columnSettingsIndex) => {
                const value = row[colIndex];
                const column = cols[colIndex];
                const clickObject = { column, value, origin: { row, cols } };
                const clickable = visualizationIsClickable(clickObject);
                const settings = columnSettings[columnSettingsIndex];
                if (settings && !settings.enabled) {
                  return null;
                }
                return (
                  <td
                    className={cx({
                      "text-brand cursor-pointer": clickable,
                      "text-right": isColumnRightAligned(column),
                    })}
                    onClick={
                      clickable ? () => onVisualizationClick(clickObject) : null
                    }
                  >
                    {formatValue(value, {
                      column: column,
                      type: "cell",
                      jsx: true,
                      rich: true,
                      // always show timestamps in local time for the audit app
                      local: true,
                      ...(settings || {}),
                    })}
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
