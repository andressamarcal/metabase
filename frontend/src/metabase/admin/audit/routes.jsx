/* @flow weak */

import React from "react";

import { Route } from "metabase/hoc/Title";
import { IndexRoute, IndexRedirect } from "react-router";
import { t } from "c-3po";

import AuditApp from "metabase/admin/audit/containers/AuditApp";

import AuditOverview from "metabase/admin/audit/pages/AuditOverview";

import AuditDatabases from "metabase/admin/audit/pages/AuditDatabases";
import AuditSchemas from "metabase/admin/audit/pages/AuditSchemas";
import AuditTables from "metabase/admin/audit/pages/AuditTables";

import AuditQuestions from "metabase/admin/audit/pages/AuditQuestions";
import AuditQuestion from "metabase/admin/audit/pages/AuditQuestion";
import AuditDashboards from "metabase/admin/audit/pages/AuditDashboards";
import AuditDashboard from "metabase/admin/audit/pages/AuditDashboard";

import AuditUsers from "metabase/admin/audit/pages/AuditUsers";
import AuditUser from "metabase/admin/audit/pages/AuditUser";

const getRoutes = store => (
  <Route path="audit" title={t`Audit`} component={AuditApp}>
    {/* <IndexRedirect to="overview" /> */}
    <IndexRedirect to="members" />

    <Route path="overview" component={AuditOverview} />

    <Route path="databases" component={AuditDatabases} />
    <Route path="schemas" component={AuditSchemas} />

    <Route path="tables">
      <IndexRoute component={AuditTables} />
      {/* <Route path=":tableId" component={AuditTable} /> */}
    </Route>

    <Route path="questions">
      <IndexRoute component={AuditQuestions} />
      <Route path=":questionId" component={AuditQuestion} />
    </Route>

    <Route path="dashboards">
      <IndexRoute component={AuditDashboards} />
      <Route path=":dashboardId" component={AuditDashboard} />
    </Route>

    <Route path="members">
      <IndexRoute component={AuditUsers} />
      <Route path=":userId" component={AuditUser} />
    </Route>
  </Route>
);

export default getRoutes;
