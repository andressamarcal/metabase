/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import * as SchemasCards from "../lib/cards/schemas";

const AuditSchemas = props => (
  <AuditContent {...props} title="Schemas" tabs={AuditSchemas.tabs} />
);

const AuditSchemasOverviewTab = () => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 0, w: 9, h: 9 }, SchemasCards.mostQueried()],
      [{ x: 9, y: 0, w: 9, h: 9 }, SchemasCards.slowestSchemas()],
    ]}
  />
);

const AuditSchemasAllTab = () => <AuditTable table={SchemasCards.table()} />;

AuditSchemas.tabs = [
  { path: "overview", title: "Overview", component: AuditSchemasOverviewTab },
  { path: "all", title: "All schemas", component: AuditSchemasAllTab },
];

export default AuditSchemas;
