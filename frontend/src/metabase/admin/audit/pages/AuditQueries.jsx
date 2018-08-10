import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditQueries = () => (
  <AuditContent title="Queries">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.queries/views-and-avg-execution-time-by-day",
        "metabase.audit.pages.queries/most-popular",
        "metabase.audit.pages.queries/slowest",
      ])}
    />
  </AuditContent>
);

export default AuditQueries;
