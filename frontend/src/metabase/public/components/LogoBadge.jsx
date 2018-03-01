/* @flow */

import React from "react";
import LogoIcon from "metabase/components/LogoIcon";

import cx from "classnames";

type Props = {
  dark: boolean,
};

const LogoBadge = ({ dark }: Props) => (
  <a
    href="http://www.metabase.com/"
    target="_blank"
    className="h4 flex text-bold align-center no-decoration"
  >
    <LogoIcon height={28} dark={dark} />
    <span className="text-small">
      <span className="ml1 md-ml2 text-grey-3">{"Powered by "}</span>
      <span className={cx({ "text-brand": !dark }, { "text-white": dark })}>
        {t`Metabase`}
      </span>
    </span>
  </a>
);

export default LogoBadge;
