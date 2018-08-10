import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

const AuditTables = () => (
  <AuditContent title="Tables">
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
  </AuditContent>
);

export default AuditTables;
