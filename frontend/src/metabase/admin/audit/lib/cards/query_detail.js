export const details = (queryHash: number) => ({
  card: {
    name: "Query details",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.query-detail/details",
      args: [queryHash],
    },
  },
});
