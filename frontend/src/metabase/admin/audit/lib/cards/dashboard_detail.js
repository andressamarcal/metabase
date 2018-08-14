/* @flow */

export const viewsByTime = (dashboardId: number) => ({
  card: {
    name: "Views by time",
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
  },
});
