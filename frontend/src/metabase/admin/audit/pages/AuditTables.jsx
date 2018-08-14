/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import * as TablesCards from "../lib/cards/tables";

const AuditTablesOverviewTab = () => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 0, w: 9, h: 9 }, TablesCards.mostQueried()],
      [{ x: 9, y: 0, w: 9, h: 9 }, TablesCards.leastQueried()],
    ]}
  />
);

const AuditTables = () => (
  <AuditContent title="Tables" tabs={AuditTables.tabs} />
);

AuditTables.tabs = [
  { path: "overview", title: "Overview", component: AuditTablesOverviewTab },
  { path: "all", title: "All tables" },
];

export default AuditTables;
