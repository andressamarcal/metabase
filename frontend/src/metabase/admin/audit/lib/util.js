export function FIXME_tempDashboard(fns, args = []) {
  return {
    ordered_cards: fns.map((fn, index) => ({
      col: 9 * (index % 2),
      row: 8 * Math.floor(index / 2),
      sizeX: 9,
      sizeY: 8,
      card: {
        name: fn,
        display: "table",
        dataset_query: {
          type: "internal",
          fn: fn,
          args: args,
        },
      },
    })),
  };
}
