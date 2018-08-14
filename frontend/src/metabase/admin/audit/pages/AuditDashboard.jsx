/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import OpenInMetabase from "../components/OpenInMetabase";

import EntityName from "metabase/entities/containers/EntityName";

import * as Urls from "metabase/lib/urls";

type Props = {
  params: { [key: string]: string },
};

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
  { path: "activity", title: "Activity" },
  { path: "details", title: "Details" },
  { path: "revisions", title: "Revision history" },
  { path: "log", title: "Audit log" },
];

export default AuditDashboardSingle;
