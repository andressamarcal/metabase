/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import EntityName from "metabase/entities/containers/EntityName";

import * as UserDetailCards from "../lib/cards/user_detail";

const AuditUserActivityTab = ({ userId }) => (
  <AuditDashboard
    cards={[
      // [{ x: 0, y: 0, w: 4, h: 4 }, UserDetailCards.questions(userId)],
      // [{ x: 4, y: 0, w: 4, h: 4 }, UserDetailCards.dashboards(userId)],
      // [{ x: 8, y: 0, w: 4, h: 4 }, UserDetailCards.pulses(userId)],
      // [{ x: 12, y: 0, w: 4, h: 4 }, UserDetailCards.collections(userId)],
      [
        { x: 0, y: 4, w: 8, h: 8 },
        UserDetailCards.mostViewedDashboards(userId),
      ],
      [{ x: 8, y: 4, w: 8, h: 8 }, UserDetailCards.mostViewedQuestions(userId)],
    ]}
  />
);

const AuditUserAuditLogTab = ({ userId }) => (
  <AuditTable table={UserDetailCards.table(userId)} />
);

type Props = {
  params: { [key: string]: string },
};

const AuditUser = ({ params }: Props) => {
  const userId = parseInt(params.userId);
  return (
    <AuditContent
      title={<EntityName entityType="users" entityId={userId} />}
      tabs={AuditUser.tabs}
      userId={userId}
    />
  );
};

AuditUser.tabs = [
  { path: "activity", title: "Activity", component: AuditUserActivityTab },
  { path: "details", title: "Account details" },
  { path: "data_permissions", title: "Data permissions" },
  { path: "collection_permissions", title: "Collection permissions" },
  { path: "made_by", title: "Made by them" },
  { path: "log", title: "Audit log", component: AuditUserAuditLogTab },
];

export default AuditUser;
