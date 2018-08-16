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

// DEPRECATED
export const queryExecutionsPerDbPerDay = () => ({
  card: {
    name: "Active users and queries per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.databases/query-executions-per-db-per-day",
      args: [],
    },
    visualization_settings: {
      "graph.dimensions": ["date", "database_id"],
      "graph.metrics": ["count"],
    },
  },
});

export const queryExecutionsByTime = () => ({
  card: {
    name: "Query executions per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.databases/query-executions-by-time",
      args: ["day"],
    },
    visualization_settings: {
      "graph.dimensions": ["date", "database_id"],
      "graph.metrics": ["count"],
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
    visualization_settings: {
      "table.columns": [
        { name: "database_id", enabled: true },
        { name: "schemas", enabled: true },
        { name: "tables", enabled: true },
        { name: "sync_schedule", enabled: true },
        { name: "added_on", enabled: true },
      ],
    },
  },
});
