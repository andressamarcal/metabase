/* @flow */

import React from "react";

import { Route } from "metabase/hoc/Title";
import { IndexRoute, IndexRedirect } from "react-router";
import { t } from "ttag";
import _ from "underscore";

import AuditApp from "metabase/admin/audit/containers/AuditApp";

import AuditOverview from "metabase/admin/audit/pages/AuditOverview";

import AuditDatabases from "metabase/admin/audit/pages/AuditDatabases";
import AuditDatabaseDetail from "metabase/admin/audit/pages/AuditDatabaseDetail";
import AuditSchemas from "metabase/admin/audit/pages/AuditSchemas";
import AuditSchemaDetail from "metabase/admin/audit/pages/AuditSchemaDetail";
import AuditTables from "metabase/admin/audit/pages/AuditTables";
import AuditTableDetail from "metabase/admin/audit/pages/AuditTableDetail";

import AuditQuestions from "metabase/admin/audit/pages/AuditQuestions";
import AuditQuestionDetail from "metabase/admin/audit/pages/AuditQuestionDetail";
import AuditDashboards from "metabase/admin/audit/pages/AuditDashboards";
import AuditDashboardDetail from "metabase/admin/audit/pages/AuditDashboardDetail";
import AuditQueryDetail from "metabase/admin/audit/pages/AuditQueryDetail";

import AuditUsers from "metabase/admin/audit/pages/AuditUsers";
import AuditUserDetail from "metabase/admin/audit/pages/AuditUserDetail";

import AuditDownloads from "metabase/admin/audit/pages/AuditDownloads";

type Page = {
  tabs?: Tab[],
};

type Tab = {
  path: string,
  title: string,
  component?: any,
};

function getPageRoutes(path, page: Page) {
  const subRoutes = [];
  // add a redirect for the default tab
  const defaultTab = getDefaultTab(page);
  if (defaultTab) {
    subRoutes.push(<IndexRedirect to={defaultTab.path} />);
  }
  // add sub routes for each tab
  if (page.tabs) {
    subRoutes.push(
      ...page.tabs.map(tab => (
        <Route path={tab.path} component={tab.component} />
      )),
    );
  }
  // if path is provided, use that, otherwise use an IndexRoute
  return path ? (
    <Route path={path} component={page}>
      {subRoutes}
    </Route>
  ) : (
    <IndexRoute component={page}>{subRoutes}</IndexRoute>
  );
}

function getDefaultTab(page: Page): ?Tab {
  // use the tab with "default = true" or the first
  return (
    _.findWhere(page.tabs, { default: true }) ||
    (page.tabs && page.tabs[0]) ||
    null
  );
}

const getRoutes = (store: any) => (
  <Route path="audit" title={t`Audit`} component={AuditApp}>
    {/* <IndexRedirect to="overview" /> */}
    <IndexRedirect to="members" />

    <Route path="overview" component={AuditOverview} />

    {getPageRoutes("databases", AuditDatabases)}
    {getPageRoutes("database/:databaseId", AuditDatabaseDetail)}
    {getPageRoutes("schemas", AuditSchemas)}
    {getPageRoutes("schema/:schemaId", AuditSchemaDetail)}
    {getPageRoutes("tables", AuditTables)}
    {getPageRoutes("table/:tableId", AuditTableDetail)}
    {getPageRoutes("dashboards", AuditDashboards)}
    {getPageRoutes("dashboard/:dashboardId", AuditDashboardDetail)}
    {getPageRoutes("questions", AuditQuestions)}
    {getPageRoutes("question/:questionId", AuditQuestionDetail)}
    {getPageRoutes("query/:queryHash", AuditQueryDetail)}
    {getPageRoutes("downloads", AuditDownloads)}
    {getPageRoutes("members", AuditUsers)}
    {getPageRoutes("member/:userId", AuditUserDetail)}
  </Route>
);

export default getRoutes;
