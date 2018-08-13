import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { auditTable } from "../lib/util";

const AuditSchemas = () => (
  <AuditContent title="Schemas">
    <AuditDashboard
      dashboard={{
        ordered_cards: [
          {
            col: 0,
            row: 0,
            sizeX: 9,
            sizeY: 9,
            card: {
              name: "Most-queried schemas",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.schemas/most-queried",
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
              name: "Slowest schemas",
              display: "row",
              dataset_query: {
                type: "internal",
                fn: "metabase.audit.pages.schemas/slowest-schemas",
                args: [],
              },
            },
          },
          auditTable(9, "Schemas", "metabase.audit.pages.schemas/table"),
        ],
      }}
    />
  </AuditContent>
);

export default AuditSchemas;
