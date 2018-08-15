/* @flow */

export const auditLog = (databaseId: number) => ({
  card: {
    name: "Audit log",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.database-detail/audit-log",
      args: [databaseId],
    },
  },
});
