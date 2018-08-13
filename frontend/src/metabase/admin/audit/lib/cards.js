function auditTable(dc, title, fn, args = []) {
  return {
    col: 0,
    sizeX: 18,
    sizeY: 18,
    ...dc,
    card: {
      name: title,
      display: "table",
      dataset_query: {
        type: "internal",
        fn: fn,
        args: args,
      },
    },
  };
}

function todo(dc, title) {
  return {
    col: 0,
    sizeX: 4,
    sizeY: 4,
    ...dc,
    card: {
      name: title,
      display: "scalar",
      dataset_query: {
        database: 1,
        type: "native",
        native: { query: "select 'todo';", template_tags: {} },
      },
    },
  };
}

export const UsersCards = {
  activeUsersAndQueriesByDay: dc => ({
    ...dc,
    card: {
      name: "Active users and queries per day",
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
  }),
  mostActive: dc => ({
    ...dc,
    card: {
      name: "Most active users",
      display: "row",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.users/most-active",
        args: [],
      },
    },
  }),
  queryExecutionTimePerUser: dc => ({
    ...dc,
    card: {
      name: "Query execution time per user",
      display: "row",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.users/query-execution-time-per-user",
        args: [],
      },
    },
  }),
  table: dc => auditTable(dc, "Users", "metabase.audit.pages.users/table"),
};

export const UserDetailCards = {
  questions: (dc, args) => todo(dc, "Questions"),
  dashboards: (dc, args) => todo(dc, "Dashboards"),
  pulses: (dc, args) => todo(dc, "Pulses"),
  collections: (dc, args) => todo(dc, "Collections"),
  table: (dc, args) => ({
    ...dc,
    card: {
      name: "Most-viewed Dashboards",
      display: "table",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.user-detail/table",
        args,
      },
    },
  }),
  mostViewedDashboards: (dc, args) => ({
    ...dc,
    card: {
      name: "Most-viewed Dashboards",
      display: "row",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.user-detail/most-viewed-dashboards",
        args,
      },
    },
  }),
  mostViewedQuestions: (dc, args) => ({
    ...dc,
    card: {
      name: "Most-viewed Queries",
      display: "row",
      dataset_query: {
        type: "internal",
        fn: "metabase.audit.pages.user-detail/most-viewed-questions",
        args,
      },
    },
  }),
  queryViews: (dc, args) =>
    auditTable(
      dc,
      "Query views",
      "metabase.audit.pages.user-detail/query-views",
      args,
    ),
};
