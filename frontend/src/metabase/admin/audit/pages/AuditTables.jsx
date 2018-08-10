import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditTables = () => (
  <AuditContent title="Tables">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.tables/most-queried",
        "metabase.audit.pages.tables/least-queried",
      ])}
    />
  </AuditContent>
);

export default AuditTables;
