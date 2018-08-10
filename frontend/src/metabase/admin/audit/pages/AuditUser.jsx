import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { auditTable } from "../lib/util";

const AuditUser = ({ params }) => (
  <AuditContent title="User">
    <AuditDashboard
      dashboard={{
        ordered_cards: [
          {
            col: 0,
            row: 0,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Most-viewed Dashboards",
              display: "table",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.user-detail/table",
                args: [parseInt(params.userId)],
              },
            },
          },

          {
            col: 0,
            row: 9,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Most-viewed Dashboards",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.user-detail/most-viewed-dashboards",
                args: [parseInt(params.userId)],
              },
            },
          },
          {
            col: 9,
            row: 9,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Most-viewed Queries",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.user-detail/most-viewed-questions",
                args: [parseInt(params.userId)],
              },
            },
          },
          auditTable(
            18,
            "Query views",
            "metabase.audit.pages.user-detail/query-views",
            [parseInt(params.userId)],
          ),
        ],
      }}
    />
  </AuditContent>
);

export default AuditUser;
