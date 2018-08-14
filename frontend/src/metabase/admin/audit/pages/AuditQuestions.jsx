/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";

const AuditQuestionsOverviewTab = () => (
  <AuditDashboard
    dashboard={{
      ordered_cards: [
        {
          col: 0,
          row: 0,
          sizeX: 18,
          sizeY: 9,
          card: {
            name: "Query views and speed per day",
            display: "line",
            dataset_query: {
              type: "internal",
              fn:
                "metabase.audit.pages.queries/views-and-avg-execution-time-by-day",
              args: [],
            },
            visualization_settings: {
              "graph.metrics": ["queries", "avg_running_time"],
              "graph.dimensions": ["database"],
              "graph.x_axis.title_text": "Time",
              "graph.x_axis.axis_enabled": true,
              "graph.y_axis.axis_enabled": true,
              "graph.y_axis.auto_split": true,
            },
          },
        },
        {
          col: 0,
          row: 9,
          sizeX: 9,
          sizeY: 9,
          card: {
            name: "Most popular queries",
            display: "row",
            dataset_query: {
              type: "internal",
              fn: "metabase.audit.pages.queries/most-popular",
              args: [],
            },
          },
        },
        {
          col: 9,
          row: 9,
          sizeX: 9,
          sizeY: 9,
          card: {
            name: "Slowest queries",
            display: "row",
            dataset_query: {
              type: "internal",
              fn: "metabase.audit.pages.queries/slowest",
              args: [],
            },
          },
        },
      ],
    }}
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
