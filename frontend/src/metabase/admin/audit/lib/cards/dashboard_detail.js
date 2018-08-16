/* @flow */

export const viewsByTime = (dashboardId: number) => ({
  card: {
    name: "Views per day",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboard-detail/views-by-time",
      args: [dashboardId, "day"], // FIXME: should this be automatic?
    },
  },
});

export const revisionHistory = (dashboardId: number) => ({
  card: {
    name: "Revision history",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboard-detail/revision-history",
      args: [dashboardId],
    },
  },
});

export const auditLog = (dashboardId: number) => ({
  card: {
    name: "Audit log",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.dashboard-detail/audit-log",
      args: [dashboardId],
    },
    visualization_settings: {
      "table.columns": [
        { name: "user_id", enabled: true },
        { name: "when", enabled: true},
      ],
    },
  },
});
