import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import { UserDetailCards } from "../lib/cards";

const UserActivityTab = ({ userId }) => (
  <AuditDashboard
    dashboard={{
      ordered_cards: [
        UserDetailCards.questions({ col: 0, row: 0, sizeX: 4, sizeY: 4 }, [
          userId,
        ]),
        UserDetailCards.dashboards({ col: 4, row: 0, sizeX: 4, sizeY: 4 }, [
          userId,
        ]),
        UserDetailCards.pulses({ col: 8, row: 0, sizeX: 4, sizeY: 4 }, [
          userId,
        ]),
        UserDetailCards.collections({ col: 12, row: 0, sizeX: 4, sizeY: 4 }, [
          userId,
        ]),
        UserDetailCards.mostViewedDashboards(
          {
            col: 0,
            row: 4,
            sizeX: 8,
            sizeY: 8,
          },
          [userId],
        ),
        UserDetailCards.mostViewedQuestions(
          {
            col: 8,
            row: 4,
            sizeX: 8,
            sizeY: 8,
          },
          [userId],
        ),
        UserDetailCards.queryViews(
          {
            row: 12,
            sizeX: 16,
          },
          [userId],
        ),
      ],
    }}
  />
);

const AuditUser = ({ params }) => {
  const userId = parseInt(params.userId);
  return (
    <AuditContent
      title="User"
      tabs={[
        "Activity",
        "Account details",
        "Data permissions",
        "Collection permissions",
        "Made by them",
        "Audit log",
      ]}
    >
      {({ currentTab }) =>
        currentTab === "Activity" ? <UserActivityTab userId={userId} /> : null
      }
    </AuditContent>
  );
};

export default AuditUser;
