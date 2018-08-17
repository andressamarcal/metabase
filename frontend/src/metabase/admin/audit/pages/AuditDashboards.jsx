/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import * as DashboardCards from "../lib/cards/dashboards";

const AuditDashboards = props => (
  <AuditContent {...props} title="Dashboards" tabs={AuditDashboards.tabs} />
);

const AuditDashboardsOverviewTab = () => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 0, w: 18, h: 4 }, DashboardCards.viewsAndSavesByTime()],
      [{ x: 0, y: 5, w: 9, h: 9 }, DashboardCards.slowest()],
      [{ x: 9, y: 5, w: 9, h: 9 }, DashboardCards.mostCommonQuestions()],
    ]}
  />
);

const AuditDashboardsAllTab = () => (
  <AuditTable table={DashboardCards.table()} />
);

AuditDashboards.tabs = [
  {
    path: "overview",
    title: "Overview",
    component: AuditDashboardsOverviewTab,
  },
  {
    path: "all",
    title: "All dashboards",
    component: AuditDashboardsAllTab,
  },
];

export default AuditDashboards;
