export const perDayBySize = () => ({
  card: {
    name: "Downloads per day by size",
    display: "scatter",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.downloads/per-day-by-size",
      args: [],
    },
  },
});

export const perUser = () => ({
  card: {
    name: "Total downloads per user",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.downloads/per-user",
      args: [],
    },
  },
});

export const bySize = () => ({
  card: {
    name: "All downloads by size",
    display: "bar",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.downloads/by-size",
      args: [],
    },
  },
});

export const table = () => ({
  card: {
    name: "Downloads",
    display: "table",
    dataset_query: {
      type: "internal",
      fn: "metabase.audit.pages.downloads/table",
      args: [],
    },
  },
});
