/* @flow */

export const auditLog = (tableId: number) => ({
  card: {
    name: "Audit log",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.table-detail/audit-log",
      args: [tableId],
    },
  },
});
