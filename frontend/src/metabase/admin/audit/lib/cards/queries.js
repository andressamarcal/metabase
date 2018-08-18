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

export const table = () => ({
  card: {
    name: "Questions",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.queries/table",
      args: [],
    },
    visualization_settings: {
      "table.columns": [
        { name: "dashboard_id", enabled: true },
        { name: "total_views", enabled: true },
        { name: "average_execution_time_ms", enabled: true },
        { name: "cards", enabled: true },
        { name: "saved_by_id", enabled: true },
        { name: "saved_on", enabled: true, date_format: "M/D/YYYY, h:mm A" },
        { name: "last_edited_on", enabled: true, date_format: "M/D/YYYY, h:mm A" },
      ],
    },
  },
});
