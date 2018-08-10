import React from "react";

import { Route } from "metabase/hoc/Title";
import { IndexRoute, IndexRedirect } from "react-router";
import { t } from "c-3po";

import AuditApp from "metabase/admin/audit/containers/AuditApp";

import AuditOverview from "metabase/admin/audit/pages/AuditOverview";

import AuditDatabases from "metabase/admin/audit/pages/AuditDatabases";
import AuditSchemas from "metabase/admin/audit/pages/AuditSchemas";
import AuditTables from "metabase/admin/audit/pages/AuditTables";

import AuditQueries from "metabase/admin/audit/pages/AuditQueries";
import AuditDashboards from "metabase/admin/audit/pages/AuditDashboards";

import AuditUsers from "metabase/admin/audit/pages/AuditUsers";
import AuditUser from "metabase/admin/audit/pages/AuditUser";

const getRoutes = store => (
  <Route path="audit" title={t`Audit`} component={AuditApp}>
    <IndexRedirect to="overview" />

    <Route path="overview" component={AuditOverview} />

    <Route path="databases" component={AuditDatabases} />
    <Route path="schemas" component={AuditSchemas} />
    <Route path="tables" component={AuditTables} />

    <Route path="queries" component={AuditQueries} />
    <Route path="dashboards" component={AuditDashboards} />

    <Route path="users">
      <IndexRoute component={AuditUsers} />
      <Route path=":userId" component={AuditUser} />
    </Route>
  </Route>
);

export default getRoutes;
