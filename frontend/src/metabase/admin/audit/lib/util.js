const columnNameToUrl = {
  user_id: value => `/admin/audit/members/${value}`,
  dashboard_id: value => `/admin/audit/dashboards/${value}`,
  card_id: value => `/admin/audit/questions/${value}`,
};

export const auditActionsForClick = ({ question, clicked }) => {
  const metricAndDimensions = [clicked].concat(clicked.dimensions || []);
  for (const { column, value } of metricAndDimensions) {
    if (column && columnNameToUrl[column.name] != null) {
      return [
        {
          name: "detail",
          title: `View this`,
          default: true,
          url() {
            return columnNameToUrl[column.name](value);
          },
        },
      ];
    }
  }
  return [];
};
