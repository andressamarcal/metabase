/* @flow weak */

import React from "react";

import Link from "metabase/components/Link";
import Icon from "metabase/components/Icon";

const OpenInMetabase = ({ ...props }) => (
  <Link {...props} className="link flex align-center">
    <Icon name="external" className="mr1" />
    Open in Metabase
  </Link>
);

export default OpenInMetabase;
