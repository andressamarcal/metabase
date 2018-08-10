import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { auditTable } from "../lib/util";

const AuditDatabases = () => (
  <AuditContent title="Databases">
    <AuditDashboard
      dashboard={{
        ordered_cards: [
          {
            col: 0,
            row: 0,
            sizeX: 18,
            sizeY: 9,
            card: {
              name: "Active users and queries per day",
              display: "bar",
              dataset_query: {
                type: "internal",
                fn:
                  "metabase.audit.pages.databases/total-query-executions-by-db",
                args: [],
              },
              visualization_settings: {
                "graph.metrics": ["queries", "avg_running_time"],
                "graph.dimensions": ["database"],
                "graph.x_axis.title_text": "Database",
                "graph.x_axis.axis_enabled": true,
                "graph.y_axis.axis_enabled": true,
                "graph.y_axis.auto_split": true,
              },
            },
          },
          {
            col: 0,
            row: 9,
            sizeX: 18,
            sizeY: 9,
            card: {
              name:
                "Active users and queries per day (FIXME: unpivot database)",
              display: "table",
              dataset_query: {
                type: "internal",
                fn:
                  "metabase.audit.pages.databases/query-executions-per-db-per-day",
                args: [],
              },
            },
          },
          auditTable(18, "Databases", "metabase.audit.pages.databases/table"),
        ],
      }}
    />
  </AuditContent>
);

export default AuditDatabases;
