/* @flow */

import React from "react";

import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import Link from "metabase/components/Link";

const fns = [
  "metabase.audit.pages.users/active-users-and-queries-by-day", // (deprecated)
  "metabase.audit.pages.users/active-and-new-by-time",
  "metabase.audit.pages.users/most-active",
  "metabase.audit.pages.users/most-saves",
  "metabase.audit.pages.users/query-execution-time-per-user",
  "metabase.audit.pages.users/table",
  "metabase.audit.pages.users/query-views",
  "metabase.audit.pages.users/dashboard-views",
  "metabase.audit.pages.question-detail/views-by-time",
  "metabase.audit.pages.question-detail/revision-history",
  "metabase.audit.pages.question-detail/audit-log",
  "metabase.audit.pages.dashboards/views-per-day",
  "metabase.audit.pages.dashboards/views-and-saves-by-time",
  "metabase.audit.pages.dashboards/most-popular",
  "metabase.audit.pages.dashboards/slowest", // (deprecated)
  "metabase.audit.pages.dashboards/most-common-questions", // (deprecated)
  "metabase.audit.pages.dashboards/table",
  "metabase.audit.pages.dashboard-detail/views-by-time",
  "metabase.audit.pages.dashboard-detail/revision-history",
  "metabase.audit.pages.dashboard-detail/audit-log",
  "metabase.audit.pages.dashboard-detail/cards",
  "metabase.audit.pages.schemas/most-queried", // (deprecated)
  "metabase.audit.pages.schemas/slowest-schemas", // (deprecated)
  "metabase.audit.pages.schemas/table", // (deprecated)
  "metabase.audit.pages.databases/total-query-executions-by-db", // (deprecated)
  "metabase.audit.pages.databases/query-executions-by-time",
  "metabase.audit.pages.databases/query-executions-per-db-per-day",
  "metabase.audit.pages.databases/table",
  "metabase.audit.pages.tables/most-queried",
  "metabase.audit.pages.tables/least-queried",
  "metabase.audit.pages.table-detail/audit-log",
  "metabase.audit.pages.queries/views-and-avg-execution-time-by-day", // (deprecated)
  "metabase.audit.pages.queries/most-popular",
  "metabase.audit.pages.queries/slowest", // (deprecated)
  "metabase.audit.pages.query-detail/details",
  "metabase.audit.pages.user-detail/table",
  "metabase.audit.pages.user-detail/most-viewed-dashboards",
  "metabase.audit.pages.user-detail/most-viewed-questions",
  "metabase.audit.pages.user-detail/query-views",
  "metabase.audit.pages.user-detail/dashboard-views",
  "metabase.audit.pages.user-detail/object-views-by-time",
  "metabase.audit.pages.user-detail/created-dashboards",
  "metabase.audit.pages.user-detail/created-questions",
  "metabase.audit.pages.database-detail/audit-log",
];

// import all modules in this directory (http://stackoverflow.com/a/31770875)
const req = require.context(
  "metabase/admin/audit/lib/cards",
  true,
  /^(.*\.(js$))[^.]*$/im,
);

const cardModules = req.keys().map(key => [key, req(key)]);

const implemented = new Set();
const modulesByPath = {};
for (const [modulePath, CardModule] of cardModules) {
  modulesByPath[modulePath] = {};
  for (const fnName in CardModule) {
    const fn = CardModule[fnName];
    const dc = fn();
    implemented.add(dc.card.dataset_query.fn);
    modulesByPath[modulePath][fnName] = fn;
  }
}

for (const fn of fns) {
  if (!implemented.has(fn)) {
    console.log("Not implemented", fn);
  } else {
    implemented.delete(fn);
  }
}
for (const fn of implemented) {
  console.log("Implemented but not longer preset", fn);
}

function getFnArgs(fn) {
  return fn
    .toString()
    .match(/function \w+\(([^)]*)\)/)[1]
    .split(/\s*,\s*/g)
    .filter(s => s);
}

export default class Demo extends React.Component {
  state = {
    argValues: {
      questionId: 1,
      databaseId: 1,
      tableId: 1,
      dashboardId: 1,
      userId: 1,
      queryHash: "x",
    },
  };
  render() {
    const { location } = this.props;
    const { argValues } = this.state;

    const { modulePath, fnName } = location.query;
    const fn = modulesByPath[modulePath] && modulesByPath[modulePath][fnName];
    const argNames = fn && getFnArgs(fn);
    const dc = fn && fn(...argNames.map(argName => argValues[argName]));

    return (
      <div>
        {cardModules.map(([modulePath, CardModule]) => (
          <div>
            <strong>{modulePath}:</strong>
            {Object.keys(CardModule).map(fnName => (
              <span className="bg-medium rounded mx1">
                <Link
                  to={`/admin/audit/demo?modulePath=${modulePath}&fnName=${fnName}`}
                >
                  {" "}
                  {fnName}
                </Link>
              </span>
            ))}
          </div>
        ))}
        {argNames &&
          argNames.map(argName => (
            <div>
              {argName}
              <input
                type={
                  typeof argValues[argName] === "number" ? "number" : "text"
                }
                value={argValues[argName]}
                onChange={e => {
                  this.setState({
                    argValues: {
                      ...argValues,
                      [argName]:
                        typeof argValues[argName] === "number"
                          ? parseFloat(e.target.value)
                          : e.target.value,
                    },
                  });
                }}
              />
            </div>
          ))}
        {dc && dc.card.display === "table" ? (
          <AuditTable table={dc} />
        ) : dc ? (
          <AuditDashboard cards={[[{ x: 0, y: 0, w: 18, h: 9 }, dc]]} />
        ) : (
          <div>click a function above</div>
        )}
      </div>
    );
  }
}
