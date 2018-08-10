import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditUsers = () => (
  <AuditContent title="Users">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.users/active-users-and-queries-by-day",
        "metabase.audit.pages.users/most-active",
        "metabase.audit.pages.users/query-execution-time-per-user",
        "metabase.audit.pages.users/table",
      ])}
    />
  </AuditContent>
);

export default AuditUsers;
