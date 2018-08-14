/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import * as DatabasesCards from "../lib/cards/databases";

const AuditDatabasesOverviewTab = () => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 0, w: 18, h: 9 }, DatabasesCards.totalQueryExecutionsByDb()],
      [
        { x: 0, y: 9, w: 18, h: 9 },
        DatabasesCards.queryExecutionsPerDbPerDay(),
      ],
    ]}
  />
);

const AuditDatabasesAllTab = () => (
  <AuditTable table={DatabasesCards.table()} />
);

const AuditDatabases = () => (
  <AuditContent title="Databases" tabs={AuditDatabases.tabs} />
);

AuditDatabases.tabs = [
  { path: "overview", title: "Overview", component: AuditDatabasesOverviewTab },
  { path: "all", title: "All databases", component: AuditDatabasesAllTab },
];

export default AuditDatabases;
