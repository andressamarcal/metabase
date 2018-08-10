import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

const AuditOverview = () => (
  <AuditContent title="Overview">
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
            sizeX: 18,
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
        ],
      }}
    />
  </AuditContent>
);

export default AuditOverview;
