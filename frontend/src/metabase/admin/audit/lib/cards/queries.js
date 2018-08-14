/* @flow */

export const viewsAndAvgExecutionTimeByDay = () => ({
  card: {
    name: "Query views and speed per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.queries/views-and-avg-execution-time-by-day",
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
});

export const mostPopular = () => ({
  card: {
    name: "Most popular queries",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.queries/most-popular",
      args: [],
    },
  },
});

export const slowest = () => ({
  card: {
    name: "Slowest queries",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.queries/slowest",
      args: [],
    },
  },
});
