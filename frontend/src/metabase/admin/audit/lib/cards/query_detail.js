/* @flow*/

export const details = (queryHash: string) => ({
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
