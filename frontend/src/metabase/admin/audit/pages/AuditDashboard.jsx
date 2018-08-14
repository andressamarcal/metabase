/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import OpenInMetabase from "../components/OpenInMetabase";

import EntityName from "metabase/entities/containers/EntityName";

import * as Urls from "metabase/lib/urls";

import * as DashboardCards from "../lib/cards/dashboard_detail";

type Props = {
  params: { [key: string]: string },
};

const AuditDashboardActivityTab = ({ dashboardId }) => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 4, w: 18, h: 4 }, DashboardCards.viewsByTime(dashboardId)],
    ]}
  />
);

const AuditDashboardRevisionsTab = ({ dashboardId }) => (
  <AuditTable table={DashboardCards.revisionHistory(dashboardId)} />
);

const AuditDashboardAuditLogTab = ({ dashboardId }) => (
  <AuditTable table={DashboardCards.auditLog(dashboardId)} />
);

const AuditDashboardSingle = ({ params }: Props) => {
  const dashboardId = parseInt(params.dashboardId);
  return (
    <AuditContent
      title={<EntityName entityType="dashboards" entityId={dashboardId} />}
      subtitle={<OpenInMetabase to={Urls.dashboard(dashboardId)} />}
      tabs={AuditDashboardSingle.tabs}
      dashboardId={dashboardId}
    />
  );
};

AuditDashboardSingle.tabs = [
  { path: "activity", title: "Activity", component: AuditDashboardActivityTab },
  { path: "details", title: "Details" },
  {
    path: "revisions",
    title: "Revision history",
    component: AuditDashboardRevisionsTab,
  },
  { path: "log", title: "Audit log", component: AuditDashboardAuditLogTab },
];

export default AuditDashboardSingle;
