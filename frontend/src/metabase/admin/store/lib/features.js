import React from "react";
import { Flex } from "grid-styled";
import { t } from "c-3po";

const SSODocs = () => (
  <Flex align="center">
    <a
      href="http://metabase.com/docs/latest/administration-guide/16-authenticating-with-saml.html"
      className="mx2 link"
    >
      SAML
    </a>
    <a
      href="http://metabase.com/docs/latest/administration-guide/18-authenticating-with-jwt.html"
      className="mx2 link"
    >
      JWT
    </a>
  </Flex>
);

const FEATURES = {
  sandboxes: {
    name: t`Data sandboxes`,
    description: t`Make sure you're showing the right people the right data with automatic and secure filters based on user attributes.`,
    icon: "lock",
    docs:
      "http://www.metabase.com/docs/latest/administration-guide/17-data-sandboxes.html",
  },
  whitelabel: {
    name: t`Whitelabeling`,
    description: t`Match Metabase to your brand with custom colors, your own logo and more.`,
    icon: "star",
    docs:
      "http://metabase.com/docs/latest/administration-guide/15-whitelabeling.html",
  },
  audit_app: {
    name: t`Auditing`,
    description: t`Keep an eye on performance and behavior with robust auditing tools.`,
    icon: "clipboard",
    docs: "https://metabase.com/offerings/auditing_info/",
  },
  sso: {
    name: t`SSO`,
    description: t`Provide easy login that works with your exisiting authentication infrastructure.`,
    icon: "group",
    docsRender: SSODocs,
  },
};

export default FEATURES;
