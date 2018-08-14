/* @flow */

export const table = (userId: number) => ({
  card: {
    name: "Most-viewed Dashboards",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.user-detail/table",
      args: [userId],
    },
  },
});

export const mostViewedDashboards = (userId: number) => ({
  card: {
    name: "Most-viewed Dashboards",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.user-detail/most-viewed-dashboards",
      args: [userId],
    },
  },
});

export const mostViewedQuestions = (userId: number) => ({
  card: {
    name: "Most-viewed Queries",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.user-detail/most-viewed-questions",
      args: [userId],
    },
  },
});

export const queryViews = (userId: number) => ({
  card: {
    name: "Query views",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.user-detail/query-views",
      args: [userId],
    },
  },
});
