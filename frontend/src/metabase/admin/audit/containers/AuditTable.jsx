/* @flow weak */

import React from "react";

import AuditDashboard from "./AuditDashboard";

const AuditTable = ({ table, ...props }) => (
  <AuditDashboard {...props} cards={[[{ x: 0, y: 0, w: 18, h: 18 }, table]]} />
);

export default AuditTable;
