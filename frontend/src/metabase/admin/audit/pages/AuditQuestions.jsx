/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

import * as QueriesCards from "../lib/cards/queries";

const AuditQuestionsOverviewTab = () => (
  <AuditDashboard
    cards={[
      [
        { x: 0, y: 0, w: 18, h: 9 },
        QueriesCards.viewsAndAvgExecutionTimeByDay(),
      ],
      [{ x: 0, y: 9, w: 9, h: 9 }, QueriesCards.mostPopular()],
      [{ x: 9, y: 9, w: 9, h: 9 }, QueriesCards.slowest()],
    ]}
  />
);

const AuditQuestions = () => (
  <AuditContent title="Questions" tabs={AuditQuestions.tabs} />
);

AuditQuestions.tabs = [
  { path: "overview", title: "Overview", component: AuditQuestionsOverviewTab },
  { path: "all", title: "All questions" },
];

export default AuditQuestions;
