/* @flow weak */

import React from "react";

import SidebarLayout from "metabase/components/SidebarLayoutFixedWidth";
import AuditSidebar from "../components/AuditSidebar";

const AuditApp = ({ children }) => (
  <SidebarLayout sidebar={<AuditSidebar />}>
    <div>{children}</div>
  </SidebarLayout>
);

export default AuditApp;
