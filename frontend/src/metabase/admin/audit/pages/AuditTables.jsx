/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

const AuditTablesOverviewTab = () => (
  <AuditDashboard
    dashboard={{
      ordered_cards: [
        {
          col: 0,
          row: 0,
          sizeX: 9,
          sizeY: 9,
          card: {
            name: "Most-queried tables",
            display: "row",
            dataset_query: {
              type: "internal",
              fn: "metabase.audit.pages.tables/most-queried",
              args: [],
            },
          },
        },
        {
          col: 9,
          row: 0,
          sizeX: 9,
          sizeY: 9,
          card: {
            name: "Least-queried tables",
            display: "row",
            dataset_query: {
              type: "internal",
              fn: "metabase.audit.pages.tables/least-queried",
              args: [],
            },
          },
        },
      ],
    }}
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
