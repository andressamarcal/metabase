/* @flow */

export const viewsByTime = (questionId: number) => ({
  card: {
    name: "Views by time",
    display: "line",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.question-detail/views-by-time",
      args: [questionId, "day"], // FIXME: should this be automatic?
    },
  },
});

export const revisionHistory = (questionId: number) => ({
  card: {
    name: "Revision history",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.question-detail/revision-history",
      args: [questionId],
    },
  },
});

export const auditLog = (questionId: number) => ({
  card: {
    name: "Audit log",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.question-detail/audit-log",
      args: [questionId],
    },
  },
});
