import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { auditTable } from "../lib/util";

const AuditUsers = () => (
  <AuditContent title="Users">
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
              display: "line",
              dataset_query: {
                type: "internal",
                fn:
                  "metabase.audit.pages.users/active-users-and-queries-by-day",
                args: [],
              },
              visualization_settings: {
                "graph.metrics": ["users", "queries"],
                "graph.dimensions": ["day"],
                "graph.x_axis.title_text": "Time",
                "graph.x_axis.axis_enabled": true,
                "graph.y_axis.title_text": "Count",
                "graph.y_axis.axis_enabled": true,
                "graph.y_axis.auto_split": false,
              },
            },
          },
          {
            col: 0,
            row: 9,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Most active users",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.users/most-active",
                args: [],
              },
            },
          },
          {
            col: 9,
            row: 9,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Query execution time per user",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.users/query-execution-time-per-user",
                args: [],
              },
            },
          },
          auditTable(18, "Users", "metabase.audit.pages.users/table"),
        ],
      }}
    />
  </AuditContent>
);

export default AuditUsers;
