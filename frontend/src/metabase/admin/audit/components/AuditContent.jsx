import React from "react";

const AuditContent = ({ title, children }) => (
  <div className="p4 flex flex-column flex-full">
    <h1>{title}</h1>
    {children}
  </div>
);

export default AuditContent;
