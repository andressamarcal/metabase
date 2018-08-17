/* @flow */

//  DEPRECATED: use `views-and-saves-by-time ` instead.
export const viewsPerDay = () => ({
  card: {
    name: "Total dashboard views per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/views-per-day",
      args: [],
    },
  },
});

export const viewsAndSavesByTime = () => ({
  card: {
    name: "Dashboard views and saves per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/views-and-saves-by-time",
      args: ["day"],
    },
    visualization_settings: {
      "graph.y_axis.axis_enabled": true,
    },
  },
});

export const mostPopularAndSpeed = () => ({
  card: {
    name: "Most popular dashboards and their avg loading times",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboards/most-popular-with-avg-speed",
      args: [],
    },
  },
});

export const mostCommonQuestions = () => ({
  card: {
    name: "Questions included the most in dashboards",
    display: "table",
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
    visualization_settings: {
      "table.columns": [
        { name: "dashboard_id", enabled: true },
        { name: "total_views", enabled: true },
        { name: "average_execution_time_ms", enabled: true },
        { name: "cards", enabled: true },
        { name: "saved_by_id", enabled: true },
        { name: "saved_on", enabled: true },
        { name: "last_edited_on", enabled: true },
      ],
    },
  },
});
