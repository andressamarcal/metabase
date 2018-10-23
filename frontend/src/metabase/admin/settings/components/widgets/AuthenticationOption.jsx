import React from "react";
import { Link } from "react-router";
import { t } from "c-3po";

const AuthenticationOption = ({ setting }) => (
  <div className="bordered rounded shadowed bg-white p4">
    <h2>{setting.authName}</h2>
    <p>{setting.authDescription}</p>
    <Link
      className="Button"
      to={`/admin/settings/authentication/${setting.authType}`}
    >
      {t`Configure`}
    </Link>
  </div>
);

export default AuthenticationOption;
