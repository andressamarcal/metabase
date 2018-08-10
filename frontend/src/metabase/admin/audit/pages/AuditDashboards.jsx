import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { auditTable } from "../lib/util";

const AuditDashboards = () => (
  <AuditContent title="Dashboards">
    <AuditDashboard
      dashboard={{
        ordered_cards: [
          {
            col: 0,
            row: 0,
            sizeX: 18,
            sizeY: 9,
            card: {
              name: "Dashboard views per day",
              display: "line",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.dashboards/views-per-day",
                args: [],
              },
            },
          },
          {
            col: 0,
            row: 9,
            sizeX: 6,
            sizeY: 9,
            card: {
              name: "Most popular dashboards",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.dashboards/most-popular",
                args: [],
              },
            },
          },
          {
            col: 6,
            row: 9,
            sizeX: 6,
            sizeY: 9,
            card: {
              name: "Slowest dashboards",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.dashboards/slowest",
                args: [],
              },
            },
          },
          {
            col: 12,
            row: 9,
            sizeX: 6,
            sizeY: 9,
            card: {
              name: "Questions included the most in dashboards",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.dashboards/most-common-questions",
                args: [],
              },
            },
          },
          auditTable(18, "Dashboards", "metabase.audit.pages.dashboards/table"),
        ],
      }}
    />
  </AuditContent>
);

export default AuditDashboards;
