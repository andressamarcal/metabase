import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditDashboards = () => (
  <AuditContent title="Dashboards">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.dashboards/views-per-day",
        "metabase.audit.pages.dashboards/most-popular",
        "metabase.audit.pages.dashboards/slowest",
        "metabase.audit.pages.dashboards/most-common-questions",
        "metabase.audit.pages.dashboards/table",
      ])}
    />
  </AuditContent>
);

export default AuditDashboards;
