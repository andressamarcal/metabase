import React from "react";

import { IndexLink } from "react-router";
import Link from "metabase/components/Link";
import cx from "classnames";

const AuditSidebarSection = ({ title, children }) => (
  <div className="pb2">
    {title && <AuditSidebarSectionTitle title={title} />}
    {children}
  </div>
);

const AuditSidebarSectionTitle = ({ title }) => (
  <div className="py1 text-smaller text-bold text-uppercase text-grey-2">
    {title}
  </div>
);

const AuditSidebarItem = ({ title, path }) => (
  <div
    className={cx("my1 cursor-pointer", {
      disabled: !path,
    })}
  >
    {path ? (
      <Link className="no-decoration" activeClassName="text-brand" to={path}>
        {title}
      </Link>
    ) : (
      <IndexLink
        className="no-decoration"
        activeClassName="text-brand"
        to="/admin/audit"
      >
        {title}
      </IndexLink>
    )}
  </div>
);

const AuditSidebar = ({ children }) => (
  <div style={{ width: 250, minHeight: "100vh" }} className="bg-grey-0 p4">
    {children}
  </div>
);

export default () => (
  <AuditSidebar>
    <AuditSidebarSection>
      <AuditSidebarItem title="Overview" path="/admin/audit/overview" />
    </AuditSidebarSection>
    <AuditSidebarSection title="Data">
      <AuditSidebarItem title="Databases" />
      <AuditSidebarItem title="Schemas" />
      <AuditSidebarItem title="Tables" />
    </AuditSidebarSection>
    <AuditSidebarSection title="Items">
      <AuditSidebarItem title="Queries" />
      <AuditSidebarItem title="Dashboards" />
    </AuditSidebarSection>
  </AuditSidebar>
);
