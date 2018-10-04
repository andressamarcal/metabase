import { t } from "c-3po";

const FEATURES = {
  sandboxes: {
    name: t`Data sandboxes`,
    description: t`Make sure you're showing the right people the right data with automatic and secure filters based on user attributes.`,
    icon: "lock",
    docs: [
      {
        link:
          "http://www.metabase.com/docs/latest/administration-guide/17-data-sandboxes.html",
      },
    ],
  },
  whitelabel: {
    name: t`White labeling`,
    description: t`Match Metabase to your brand with custom colors, your own logo and more.`,
    icon: "star",
    docs: [
      {
        link:
          "http://metabase.com/docs/latest/administration-guide/15-whitelabeling.html",
      },
    ],
  },
  "audit-app": {
    name: t`Auditing`,
    description: t`Keep an eye on performance and behavior with robust auditing tools.`,
    icon: "clipboard",
    info: [{ link: "https://metabase.com/offerings/auditing_info/" }],
  },
  sso: {
    name: t`Single sign-on`,
    description: t`Provide easy login that works with your exisiting authentication infrastructure.`,
    icon: "group",
    docs: [
      {
        title: "SAML",
        link:
          "http://metabase.com/docs/latest/administration-guide/16-authenticating-with-saml.html",
      },
      {
        title: "JWT",
        link:
          "http://metabase.com/docs/latest/administration-guide/18-authenticating-with-jwt.html",
      },
    ],
  },
};

export default FEATURES;
