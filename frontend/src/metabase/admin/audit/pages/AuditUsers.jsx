import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { UsersCards } from "../lib/cards";

const UsersOverviewTab = () => (
  <AuditDashboard
    dashboard={{
      ordered_cards: [
        UsersCards.activeUsersAndQueriesByDay({
          col: 0,
          row: 0,
          sizeX: 18,
          sizeY: 9,
        }),
        UsersCards.mostActive({
          col: 0,
          row: 9,
          sizeX: 9,
          sizeY: 9,
        }),
        UsersCards.queryExecutionTimePerUser({
          col: 9,
          row: 9,
          sizeX: 9,
          sizeY: 9,
        }),
      ],
    }}
  />
);

const UsersAuditLogTab = () => (
  <AuditDashboard
    dashboard={{
      ordered_cards: [
        UsersCards.table({
          row: 0,
        }),
      ],
    }}
  />
);

const AuditUsers = () => (
  <AuditContent title="Users" tabs={["Overview", "All members", "Audit log"]}>
    {({ currentTab }) =>
      currentTab === "Overview" ? (
        <UsersOverviewTab />
      ) : currentTab === "Audit log" ? (
        <UsersAuditLogTab />
      ) : null
    }
  </AuditContent>
);

export default AuditUsers;
