/* @flow weak */

import React from "react";

import { Dashboard } from "metabase/dashboard/containers/Dashboard";
import DashboardData from "metabase/dashboard/hoc/DashboardData";

const DashboardWithData = DashboardData(Dashboard);

const AuditTable = ({ table, ...props }) => (
  <DashboardWithData
    style={{ backgroundColor: "transparent" }}
    // HACK: to get inline dashboards working quickly
    dashboardId={{
      ordered_cards: [{ ...table, row: 0, col: 0, sizeX: 18, sizeY: 18 }],
    }}
    {...props}
  />
);

export default AuditTable;
