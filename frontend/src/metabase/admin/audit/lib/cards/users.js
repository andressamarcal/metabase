/* @flow */

export const activeAndNewByTime = () => ({
  card: {
    name: "Active members and new members per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/active-and-new-by-time",
      args: ["day"],
    },
    visualization_settings: {
      "graph.metrics": ["active_users", "new_users"],
      "graph.dimensions": ["date"],
      "graph.x_axis.title_text": "Time",
      "graph.x_axis.axis_enabled": true,
      "graph.y_axis.title_text": "Count",
      "graph.y_axis.axis_enabled": true,
      "graph.y_axis.auto_split": false,
    },
  },
});

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
    visualization_settings: {
      "graph.x_axis.axis_enabled": true,
      "graph.x_axis.title_text": "Views",
    },
  },
});

export const mostSaves = () => ({
  card: {
    name: "Members who are creating the most things",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/most-saves",
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

export const table = queryString => ({
  card: {
    name: "Users",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/table",
      args: queryString ? [queryString] : [],
    },
    visualization_settings: {
      "table.columns": [
        { name: "user_id", enabled: true },
        { name: "groups", enabled: true },
        { name: "date_joined", enabled: true },
        { name: "last_active", enabled: true },
        { name: "signup_method", enabled: true },
      ],
    },
  },
});

export const auditLog = () => ({
  card: {
    name: "Query views",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/query-views",
      args: [],
    },
    visualization_settings: {
      "table.columns": [
        { name: "card_id", enabled: true },
        { name: "viewed_by_id", enabled: true },
        { name: "type", enabled: true },
        { name: "database_id", enabled: true },
        { name: "table_id", enabled: true },
        { name: "collection_id", enabled: true },
        { name: "viewed_on", enabled: true },
      ],
    },
  },
  series: [
    {
      name: "Dashboard views",
      display: "table",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.users/dashboard-views",
        args: [],
      },
    },
  ],
});

export const dashboardViews = () => ({
  card: {
    name: "Dashboard views",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.users/dashboard-views",
      args: [],
    },
  },
});
