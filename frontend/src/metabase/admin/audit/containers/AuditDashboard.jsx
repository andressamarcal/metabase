/* @flow weak */

import React from "react";

import { Dashboard } from "metabase/dashboard/containers/Dashboard";
import DashboardData from "metabase/dashboard/hoc/DashboardData";

const DashboardWithData = DashboardData(Dashboard);

const AuditDashboards = ({ cards, ...props }) => (
  <DashboardWithData
    style={{ backgroundColor: "transparent" }}
    // HACK: to get inline dashboards working quickly
    dashboardId={{
      ordered_cards: cards.map(([{ x, y, w, h }, dc]) => ({
        col: x,
        row: y,
        sizeX: w,
        sizeY: h,
        ...dc,
      })),
    }}
    {...props}
  />
);

export default AuditDashboards;
