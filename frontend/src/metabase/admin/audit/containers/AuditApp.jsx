/* @flow weak */

import React from "react";

import SidebarLayout from "metabase/components/SidebarLayout";
import AuditSidebar from "../components/AuditSidebar";

const AuditApp = ({ children }) => (
  <SidebarLayout sidebar={<AuditSidebar />}>{children}</SidebarLayout>
);

export default AuditApp;
