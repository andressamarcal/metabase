const columnNameToUrl = {
  user_id: value => `/admin/audit/member/${value}`,
  dashboard_id: value => `/admin/audit/dashboard/${value}`,
  card_id: value => `/admin/audit/question/${value}`,
  database_id: value => `/admin/audit/database/${value}`,
  schema: value => `/admin/audit/schema/${value}`,
  table_id: value => `/admin/audit/table/${value}`,
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
