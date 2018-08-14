/* @flow */

export const totalQueryExecutionsByDb = () => ({
  card: {
    name: "Active users and queries per day",
    display: "bar",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.databases/total-query-executions-by-db",
      args: [],
    },
    visualization_settings: {
      "graph.metrics": ["queries", "avg_running_time"],
      "graph.dimensions": ["database"],
      "graph.x_axis.title_text": "Database",
      "graph.x_axis.axis_enabled": true,
      "graph.y_axis.axis_enabled": true,
      "graph.y_axis.auto_split": true,
    },
  },
});

export const queryExecutionsPerDbPerDay = () => ({
  card: {
    name: "Active users and queries per day (FIXME: unpivot database)",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.databases/query-executions-per-db-per-day",
      args: [],
    },
  },
});

export const table = () => ({
  card: {
    name: "Databases",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.databases/table",
      args: [],
    },
  },
});
