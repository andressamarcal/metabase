/* @flow */

export const mostQueried = () => ({
  card: {
    name: "Most-queried schemas",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.schemas/most-queried",
      args: [],
    },
  },
});

export const slowestSchemas = () => ({
  card: {
    name: "Slowest schemas",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.schemas/slowest-schemas",
      args: [],
    },
  },
});

export const table = () => ({
  card: {
    name: "Schemas",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.schemas/table",
      args: [],
    },
  },
});
