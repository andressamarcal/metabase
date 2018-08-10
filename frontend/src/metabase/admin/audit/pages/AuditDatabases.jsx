import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditDatabases = () => (
  <AuditContent title="Databases">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.databases/total-query-executions-by-db",
        "metabase.audit.pages.databases/query-executions-per-db-per-day",
        "metabase.audit.pages.databases/table",
      ])}
    />
  </AuditContent>
);

export default AuditDatabases;
