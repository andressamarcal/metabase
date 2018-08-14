/* @flow */

export const activeUsersAndQueriesByDay = () => ({
  card: {
    name: "Active members and queries per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/active-users-and-queries-by-day",
      args: [],
    },
    visualization_settings: {
      "graph.metrics": ["users", "queries"],
      "graph.dimensions": ["day"],
      "graph.x_axis.title_text": "Time",
      "graph.x_axis.axis_enabled": true,
      "graph.y_axis.title_text": "Count",
      "graph.y_axis.axis_enabled": true,
      "graph.y_axis.auto_split": false,
    },
  },
});

export const mostActive = () => ({
  card: {
    name: "Members who are looking at the most things",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/most-active",
      args: [],
    },
  },
});

export const queryExecutionTimePerUser = () => ({
  card: {
    name: "Query execution time per member",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/query-execution-time-per-user",
      args: [],
    },
  },
});

export const table = () => ({
  card: {
    name: "Users",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/table",
      args: [],
    },
  },
});
