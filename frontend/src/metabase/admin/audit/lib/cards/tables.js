/* @flow */

export const mostQueried = () => ({
  card: {
    name: "Most-queried tables",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.tables/most-queried",
      args: [],
    },
  },
});

export const leastQueried = () => ({
  card: {
    name: "Least-queried tables",
    display: "row",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.tables/least-queried",
      args: [],
    },
  },
});

export const table = (searchString?: string) => ({
  card: {
    name: "Tables",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.tables/table",
      args: searchString ? [searchString] : [],
    },
  },
});
