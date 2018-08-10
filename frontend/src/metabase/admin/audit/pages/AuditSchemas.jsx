import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { FIXME_tempDashboard } from "../lib/util";

const AuditSchemas = () => (
  <AuditContent title="Schemas">
    <AuditDashboard
      dashboard={FIXME_tempDashboard([
        "metabase.audit.pages.schemas/most-queried",
        "metabase.audit.pages.schemas/slowest-schemas",
        "metabase.audit.pages.schemas/table",
      ])}
    />
  </AuditContent>
);

export default AuditSchemas;
