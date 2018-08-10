import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditUser = ({ params }) => (
  <AuditContent title="User">
    <AuditDashboard
      dashboard={FIXME_tempDashboard(
        [
          "metabase.audit.pages.user-detail/table",
          "metabase.audit.pages.user-detail/most-viewed-dashboards",
          "metabase.audit.pages.user-detail/most-viewed-questions",
          "metabase.audit.pages.user-detail/query-views",
        ],
        [parseInt(params.userId)],
      )}
    />
  </AuditContent>
);

export default AuditUser;
