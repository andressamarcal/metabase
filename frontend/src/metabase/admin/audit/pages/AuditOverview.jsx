import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const ALL = [
  "metabase.audit.pages.users/active-users-and-queries-by-day",
  "metabase.audit.pages.users/most-active",
];

const AuditOverview = () => (
  <AuditContent title="Overview">
    <AuditDashboard dashboard={FIXME_tempDashboard(ALL)} />
  </AuditContent>
);

export default AuditOverview;
