/* @flow weak */
import React from "react";

import { Dashboard } from "metabase/dashboard/containers/Dashboard";
import DashboardData from "metabase/dashboard/hoc/DashboardData";

const DashboardWithData = DashboardData(Dashboard);

const AuditDashboards = ({ dashboard, ...props }) => (
  <DashboardWithData
    // HACK: to get inline dashboards working quickly
    dashboardId={dashboard}
    {...props}
  />
);

export default AuditDashboards;
