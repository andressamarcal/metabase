export function auditTable(row, title, fn, args = []) {
  return {
    col: 0,
    row: row,
    sizeX: 18,
    sizeY: 18,
    card: {
      name: title,
      display: "table",
      dataset_query: {
        type: "internal",
        fn: fn,
        args: args,
      },
    },
  };
}
