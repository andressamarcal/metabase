import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "./AuditDashboard";

const AUDIT_OVERVIEW_DASHBOARD = {
  ordered_cards: [
    {
      col: 0,
      row: 0,
      sizeX: 4,
      sizeY: 4,
      card: {
        name: "Orders, Count",
        display: "scalar",
        dataset_query: {
          query: { source_table: 2, aggregation: [["count"]] },
          type: "query",
          database: 1,
        },
      },
    },
    {
      col: 4,
      row: 0,
      sizeX: 4,
      sizeY: 4,
      card: {
        name: "Orders, Count",
        display: "scalar",
        dataset_query: {
          type: "endpoint",
          endpoint: "SOMETHING",
        },
      },
    },
  ],
};

const AuditOverview = () => (
  <AuditContent title="Overview">
    <AuditDashboard dashboard={AUDIT_OVERVIEW_DASHBOARD} />
  </AuditContent>
);

export default AuditOverview;
