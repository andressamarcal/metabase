/* @flow weak */

import React from "react";

import { Dashboard } from "metabase/dashboard/containers/Dashboard";
import DashboardData from "metabase/dashboard/hoc/DashboardData";

const DashboardWithData = DashboardData(Dashboard);

const columnNameToUrl = {
  user_id: value => `/admin/audit/members/${value}`,
  dashboard_id: value => `/admin/audit/dashboards/${value}`,
  card_id: value => `/admin/audit/questions/${value}`,
};

const AuditDashboards = ({ cards, ...props }) => (
  <DashboardWithData
    style={{ backgroundColor: "transparent", padding: 0 }}
    // HACK: to get inline dashboards working quickly
    dashboardId={{
      ordered_cards: cards.map(([{ x, y, w, h }, dc]) => ({
        col: x,
        row: y,
        sizeX: w,
        sizeY: h,
        visualization_settings: {
          // we want to hide the background to help make the charts feel
          // like they're part of the page, so turn off the background
          "dashcard.background": false,
        },
        ...dc,
      })),
    }}
    actionsForClick={({ question, clicked }) => {
      const metricAndDimensions = [clicked].concat(clicked.dimensions || []);
      for (const { column, value } of metricAndDimensions) {
        if (column && columnNameToUrl[column.name] != null) {
          return [
            {
              name: "detail",
              title: `View this`,
              default: true,
              url() {
                return columnNameToUrl[column.name](value);
              },
            },
          ];
        }
      }
      return [];
    }}
    {...props}
  />
);

export default AuditDashboards;
