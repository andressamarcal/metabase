const columnNameToUrl = {
  user_id: value => `/admin/audit/member/${value}`,
  viewed_by_id: value => `/admin/audit/member/${value}`,
  dashboard_id: value => `/admin/audit/dashboard/${value}`,
  card_id: value => `/admin/audit/question/${value}`,
  database_id: value => `/admin/audit/database/${value}`,
  // NOTE: disable schema links until schema detail is implemented
  // schema: value => `/admin/audit/schema/${value}`,
  table_id: value => `/admin/audit/table/${value}`,
  // NOTE: query_hash uses standard Base64 encoding which isn't URL safe so make sure to escape it
  query_hash: value => `/admin/audit/query/${encodeURIComponent(value)}`,
};

export const auditActionsForClick = ({ question, clicked }) => {
  const metricAndDimensions = [clicked].concat(clicked.dimensions || []);
  for (const { column, value } of metricAndDimensions) {
    if (column && columnNameToUrl[column.name] != null && value != null) {
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
