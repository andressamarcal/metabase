/* @flow */

export const viewsPerDay = () => ({
  card: {
    name: "Dashboard views per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/views-per-day",
      args: [],
    },
  },
});

export const mostPopular = () => ({
  card: {
    name: "Most popular dashboards",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/most-popular",
      args: [],
    },
  },
});

export const slowest = () => ({
  card: {
    name: "Slowest dashboards",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/slowest",
      args: [],
    },
  },
});

export const mostCommonQuestions = () => ({
  card: {
    name: "Questions included the most in dashboards",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/most-common-questions",
      args: [],
    },
  },
});

export const table = () => ({
  card: {
    name: "Dashboards",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/table",
      args: [],
    },
  },
});
