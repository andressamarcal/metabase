import React from "react";

import { registerVisualization } from "metabase/visualizations/index";

import { formatColumn, formatValue } from "metabase/lib/formatting";

export default class AuditTableVisualization extends React.Component {
  static identifier = "audit-table";
  static noHeader = true;
  render() {
    const {
      series: [{ data: { cols, rows } }],
      visualizationIsClickable,
      onVisualizationClick,
    } = this.props;
    return (
      <table className="ContentTable">
        <thead>
          <tr>{cols.map(col => <th>{formatColumn(col)}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr>
              {cols.map((column, colIndex) => {
                const value = row[colIndex];
                const clickObject = { column, value };
                const clickable = visualizationIsClickable(clickObject);
                return (
                  <td
                    className={clickable ? "text-brand cursor-pointer" : null}
                    onClick={
                      clickable ? () => onVisualizationClick(clickObject) : null
                    }
                  >
                    {formatValue(value, { column })}
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
