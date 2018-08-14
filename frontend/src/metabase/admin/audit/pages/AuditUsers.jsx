/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import * as UsersCards from "../lib/cards/users";

const AuditUsersOverviewTab = () => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 0, w: 18, h: 9 }, UsersCards.activeUsersAndQueriesByDay()],
      [{ x: 0, y: 9, w: 9, h: 9 }, UsersCards.mostActive()],
      [{ x: 9, y: 9, w: 9, h: 9 }, UsersCards.queryExecutionTimePerUser()],
    ]}
  />
);

const AuditUsersAllTab = () => <AuditTable table={UsersCards.table()} />;

const AuditUsersAuditLogTab = () => <AuditTable table={UsersCards.table()} />;

const AuditUsers = () => <AuditContent title="Team members" tabs={AuditUsers.tabs} />;

AuditUsers.tabs = [
  { path: "overview", title: "Overview", component: AuditUsersOverviewTab },
  { path: "all", title: "All members", component: AuditUsersAllTab },
  { path: "log", title: "Audit log", component: AuditUsersAuditLogTab },
];

export default AuditUsers;
