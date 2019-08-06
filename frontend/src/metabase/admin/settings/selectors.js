import _ from "underscore";
import { createSelector } from "reselect";
import MetabaseSettings from "metabase/lib/settings";
import { t } from "ttag";
import CustomGeoJSONWidget from "./components/widgets/CustomGeoJSONWidget.jsx";
import {
  PublicLinksDashboardListing,
  PublicLinksQuestionListing,
  EmbeddedQuestionListing,
  EmbeddedDashboardListing,
} from "./components/widgets/PublicLinksListing.jsx";
import SecretKeyWidget from "./components/widgets/SecretKeyWidget.jsx";
import EmbeddingLegalese from "./components/widgets/EmbeddingLegalese";
import EmbeddingLevel from "./components/widgets/EmbeddingLevel";
import GroupMappingsWidget from "./components/widgets/GroupMappingsWidget";
import FormattingWidget from "./components/widgets/FormattingWidget";

import LogoUpload from "./components/widgets/LogoUpload";
import ColorSchemeWidget from "./components/widgets/ColorSchemeWidget";
import AuthenticationOption from "./components/widgets/AuthenticationOption";

import { UtilApi } from "metabase/services";

/* Note - do not translate slugs */
const SECTIONS = [
  {
    name: t`Setup`,
    slug: "setup",
    settings: [],
  },
  {
    name: t`General`,
    slug: "general",
    settings: [
      {
        key: "site-name",
        display_name: t`Site Name`,
        type: "string",
      },
      {
        key: "site-url",
        display_name: t`Site URL`,
        type: "string",
      },
      {
        key: "admin-email",
        display_name: t`Email Address for Help Requests`,
        type: "string",
      },
      {
        key: "report-timezone",
        display_name: t`Report Timezone`,
        type: "select",
        options: [
          { name: t`Database Default`, value: "" },
          ...MetabaseSettings.get("timezones"),
        ],
        note: t`Not all databases support timezones, in which case this setting won't take effect.`,
        allowValueCollection: true,
      },
      {
        key: "site-locale",
        display_name: t`Language`,
        type: "select",
        options: (MetabaseSettings.get("available_locales") || []).map(
          ([value, name]) => ({ name, value }),
        ),
        defaultValue: "en",
        getHidden: () => MetabaseSettings.get("available_locales").length < 2,
      },
      {
        key: "anon-tracking-enabled",
        display_name: t`Anonymous Tracking`,
        type: "boolean",
      },
      {
        key: "humanization-strategy",
        display_name: t`Friendly Table and Field Names`,
        type: "select",
        options: [
          { value: "advanced", name: t`Enabled` },
          {
            value: "simple",
            name: t`Only replace underscores and dashes with spaces`,
          },
          { value: "none", name: t`Disabled` },
        ],
        defaultValue: "advanced",
      },
      {
        key: "enable-nested-queries",
        display_name: t`Enable Nested Queries`,
        type: "boolean",
      },
      {
        key: "enable-xrays",
        display_name: t`Enable X-ray features`,
        type: "boolean",
      },
    ],
  },
  {
    name: t`Updates`,
    slug: "updates",
    settings: [
      {
        key: "check-for-updates",
        display_name: t`Check for updates`,
        type: "boolean",
      },
    ],
  },
  {
    name: t`Email`,
    slug: "email",
    settings: [
      {
        key: "email-smtp-host",
        display_name: t`SMTP Host`,
        placeholder: "smtp.yourservice.com",
        type: "string",
        required: true,
        autoFocus: true,
      },
      {
        key: "email-smtp-port",
        display_name: t`SMTP Port`,
        placeholder: "587",
        type: "number",
        required: true,
        validations: [["integer", t`That's not a valid port number`]],
      },
      {
        key: "email-smtp-security",
        display_name: t`SMTP Security`,
        description: null,
        type: "radio",
        options: { none: "None", ssl: "SSL", tls: "TLS", starttls: "STARTTLS" },
        defaultValue: "none",
      },
      {
        key: "email-smtp-username",
        display_name: t`SMTP Username`,
        description: null,
        placeholder: "youlooknicetoday",
        type: "string",
      },
      {
        key: "email-smtp-password",
        display_name: t`SMTP Password`,
        description: null,
        placeholder: "Shh...",
        type: "password",
      },
      {
        key: "email-from-address",
        display_name: t`From Address`,
        placeholder: "metabase@yourcompany.com",
        type: "string",
        required: true,
        validations: [["email", t`That's not a valid email address`]],
      },
    ],
  },
  {
    name: "Slack",
    slug: "slack",
    settings: [
      {
        key: "slack-token",
        display_name: t`Slack API Token`,
        description: "",
        placeholder: t`Enter the token you received from Slack`,
        type: "string",
        required: false,
        autoFocus: true,
      },
      {
        key: "metabot-enabled",
        display_name: "MetaBot",
        type: "boolean",
        // TODO: why do we have "defaultValue" here in addition to the "default" specified by the backend?
        defaultValue: false,
        required: true,
        autoFocus: false,
      },
    ],
  },
  {
    name: t`Single Sign-On`,
    slug: "single_sign_on",
    sidebar: false,
    settings: [
      {
        key: "google-auth-client-id",
      },
      {
        key: "google-auth-auto-create-accounts-domain",
      },
    ],
  },
  {
    name: t`Authentication`,
    slug: "authentication",
    settings: [
      {
        authName: t`Sign in with Google`,
        authDescription: t`Allows users with existing Metabase accounts to login with a Google account that matches their email address in addition to their Metabase username and password.`,
        authType: "google",
        authEnabled: settings => !!settings["google-auth-client-id"],
        widget: AuthenticationOption,
      },
      {
        authName: t`LDAP`,
        authDescription: t`Allows users within your LDAP directory to log in to Metabase with their LDAP credentials, and allows automatic mapping of LDAP groups to Metabase groups.`,
        authType: "ldap",
        authEnabled: settings => settings["ldap-enabled"],
        widget: AuthenticationOption,
      },
      {
        authName: t`SAML`,
        authDescription: t`Allows users to login via a SAML Identity Provider.`,
        authType: "saml",
        authEnabled: settings => settings["saml-enabled"],
        widget: AuthenticationOption,
        getHidden: () => !MetabaseSettings.hasPremiumFeature("sso"),
      },
      {
        authName: t`JWT`,
        authDescription: t`Allows users to login via a JWT Identity Provider.`,
        authType: "jwt",
        authEnabled: settings => settings["jwt-enabled"],
        widget: AuthenticationOption,
        getHidden: () => !MetabaseSettings.hasPremiumFeature("sso"),
      },
      {
        key: "enable-password-login",
        display_name: t`Enable Password Authentication`,
        description: t`Turn this off to force users to log in with your auth system instead of email and password.`,
        type: "boolean",
        getHidden: settings =>
          !settings["google-auth-client-id"] &&
          !settings["ldap-enabled"] &&
          !settings["saml-enabled"] &&
          !settings["jwt-enabled"],
      },
    ],
  },
  {
    name: t`LDAP`,
    slug: "ldap",
    sidebar: false,
    settings: [
      {
        key: "ldap-enabled",
        display_name: t`LDAP Authentication`,
        description: null,
        type: "boolean",
      },
      {
        key: "ldap-host",
        display_name: t`LDAP Host`,
        placeholder: "ldap.yourdomain.org",
        type: "string",
        required: true,
        autoFocus: true,
      },
      {
        key: "ldap-port",
        display_name: t`LDAP Port`,
        placeholder: "389",
        type: "string",
        validations: [["integer", t`That's not a valid port number`]],
      },
      {
        key: "ldap-security",
        display_name: t`LDAP Security`,
        description: null,
        type: "radio",
        options: { none: "None", ssl: "SSL", starttls: "StartTLS" },
        defaultValue: "none",
      },
      {
        key: "ldap-bind-dn",
        display_name: t`Username or DN`,
        type: "string",
      },
      {
        key: "ldap-password",
        display_name: t`Password`,
        type: "password",
      },
      {
        key: "ldap-user-base",
        display_name: t`User search base`,
        type: "string",
        required: true,
      },
      {
        key: "ldap-user-filter",
        display_name: t`User filter`,
        type: "string",
        validations: [
          value =>
            (value.match(/\(/g) || []).length !==
            (value.match(/\)/g) || []).length
              ? t`Check your parentheses`
              : null,
        ],
      },
      {
        key: "ldap-attribute-email",
        display_name: t`Email attribute`,
        type: "string",
      },
      {
        key: "ldap-attribute-firstname",
        display_name: t`First name attribute`,
        type: "string",
      },
      {
        key: "ldap-attribute-lastname",
        display_name: t`Last name attribute`,
        type: "string",
      },
      {
        key: "ldap-group-sync",
        display_name: t`Synchronize group memberships`,
        description: null,
        widget: GroupMappingsWidget,
        props: {
          mappingSetting: "ldap-group-mappings",
          groupHeading: t`Distinguished Name`,
          groupPlaceholder: "cn=People,ou=Groups,dc=metabase,dc=com",
        },
      },
      {
        key: "ldap-group-base",
        display_name: t`Group search base`,
        type: "string",
      },
      {
        key: "ldap-group-mappings",
      },
    ],
  },

  {
    name: t`SAML`,
    slug: "saml",
    sidebar: false,
    settings: [
      {
        key: "saml-enabled",
        display_name: t`SAML Authentication`,
        description: null,
        type: "boolean",
      },
      {
        key: "saml-identity-provider-uri",
        display_name: t`SAML Identity Provider URI`,
        placeholder: "https://saml.yourdomain.org",
        type: "string",
        required: true,
        autoFocus: true,
      },
      {
        key: "saml-identity-provider-certificate",
        display_name: t`SAML Identity Provider Certificate`,
        type: "text",
        required: true,
      },
      {
        key: "saml-application-name",
        display_name: t`SAML Application Name`,
        type: "string",
      },
      {
        key: "saml-keystore-path",
        display_name: t`SAML Keystore Path`,
        type: "string",
      },
      {
        key: "saml-keystore-password",
        display_name: t`SAML Keystore Password`,
        placeholder: "Shh...",
        type: "password",
      },
      {
        key: "saml-keystore-alias",
        display_name: t`SAML Keystore Alias`,
        type: "string",
      },
      {
        key: "saml-attribute-email",
        display_name: t`Email attribute`,
        type: "string",
      },
      {
        key: "saml-attribute-firstname",
        display_name: t`First name attribute`,
        type: "string",
      },
      {
        key: "saml-attribute-lastname",
        display_name: t`Last name attribute`,
        type: "string",
      },
      {
        key: "saml-group-sync",
        display_name: t`Synchronize group memberships`,
        description: null,
        widget: GroupMappingsWidget,
        props: {
          mappingSetting: "saml-group-mappings",
          groupHeading: t`Group Name`,
          groupPlaceholder: "Group Name",
        },
      },
      {
        key: "saml-attribute-group",
        display_name: t`Group attribute name`,
        type: "string",
      },
      {
        key: "saml-group-mappings",
      },
    ],
  },
  {
    name: t`JWT`,
    slug: "jwt",
    sidebar: false,
    settings: [
      {
        key: "jwt-enabled",
        description: null,
        getHidden: settings => settings["jwt-enabled"],
        onChanged: async (
          oldValue,
          newValue,
          settingsValues,
          onChangeSetting,
        ) => {
          // Generate a secret key if none already exists
          if (!oldValue && newValue && !settingsValues["jwt-shared-secret"]) {
            const result = await UtilApi.random_token();
            await onChangeSetting("jwt-shared-secret", result.token);
          }
        },
      },
      {
        key: "jwt-enabled",
        display_name: t`JWT Authentication`,
        type: "boolean",
        getHidden: settings => !settings["jwt-enabled"],
      },
      {
        key: "jwt-identity-provider-uri",
        display_name: t`JWT Identity Provider URI`,
        placeholder: "https://jwt.yourdomain.org",
        type: "string",
        required: true,
        autoFocus: true,
        getHidden: settings => !settings["jwt-enabled"],
      },
      {
        key: "jwt-shared-secret",
        display_name: t`String used by the JWT signing key`,
        type: "text",
        required: true,
        widget: SecretKeyWidget,
        getHidden: settings => !settings["jwt-enabled"],
      },
      {
        key: "jwt-attribute-email",
        display_name: t`Email attribute`,
        type: "string",
      },
      {
        key: "jwt-attribute-firstname",
        display_name: t`First name attribute`,
        type: "string",
      },
      {
        key: "jwt-attribute-lastname",
        display_name: t`Last name attribute`,
        type: "string",
      },
      {
        key: "jwt-group-sync",
        display_name: t`Synchronize group memberships`,
        description: null,
        widget: GroupMappingsWidget,
        props: {
          mappingSetting: "jwt-group-mappings",
          groupHeading: t`Group Name`,
          groupPlaceholder: "Group Name",
        },
      },
      {
        key: "jwt-group-mappings",
      },
    ],
  },
  {
    name: t`Maps`,
    slug: "maps",
    settings: [
      {
        key: "map-tile-server-url",
        display_name: t`Map tile server URL`,
        note: t`Metabase uses OpenStreetMaps by default.`,
        type: "string",
      },
      {
        key: "custom-geojson",
        display_name: t`Custom Maps`,
        description: t`Add your own GeoJSON files to enable different region map visualizations`,
        widget: CustomGeoJSONWidget,
        noHeader: true,
      },
    ],
  },
  {
    name: t`Formatting`,
    slug: "formatting",
    settings: [
      {
        display_name: t`Formatting Options`,
        description: "",
        key: "custom-formatting",
        widget: FormattingWidget,
      },
    ],
  },
  {
    name: t`Public Sharing`,
    slug: "public_sharing",
    settings: [
      {
        key: "enable-public-sharing",
        display_name: t`Enable Public Sharing`,
        type: "boolean",
      },
      {
        key: "-public-sharing-dashboards",
        display_name: t`Shared Dashboards`,
        widget: PublicLinksDashboardListing,
        getHidden: settings => !settings["enable-public-sharing"],
      },
      {
        key: "-public-sharing-questions",
        display_name: t`Shared Questions`,
        widget: PublicLinksQuestionListing,
        getHidden: settings => !settings["enable-public-sharing"],
      },
    ],
  },
  {
    name: t`Embedding in other Applications`,
    slug: "embedding_in_other_applications",
    settings: [
      {
        key: "enable-embedding",
        description: null,
        widget: EmbeddingLegalese,
        getHidden: settings => settings["enable-embedding"],
        onChanged: async (
          oldValue,
          newValue,
          settingsValues,
          onChangeSetting,
        ) => {
          // Generate a secret key if none already exists
          if (
            !oldValue &&
            newValue &&
            !settingsValues["embedding-secret-key"]
          ) {
            const result = await UtilApi.random_token();
            await onChangeSetting("embedding-secret-key", result.token);
          }
        },
      },
      {
        key: "enable-embedding",
        display_name: t`Enable Embedding Metabase in other Applications`,
        type: "boolean",
        getHidden: settings => !settings["enable-embedding"],
      },
      {
        widget: EmbeddingLevel,
        // WHITELABEL: always hide this setting
        getHidden: () => true,
      },
      {
        key: "embedding-secret-key",
        display_name: t`Embedding secret key`,
        widget: SecretKeyWidget,
        getHidden: settings => !settings["enable-embedding"],
      },
      {
        key: "embedding-app-origin",
        display_name: t`Embedding the entire Metabase app`,
        description: t`If you want to embed all of Metabase, enter the origin (protocol and host only) of the website where you want to allow embedding in an iFrame.`,
        placeholder: "https://example.com",
        type: "string",
        getHidden: settings => !settings["enable-embedding"],
      },
      {
        key: "-embedded-dashboards",
        display_name: t`Embedded Dashboards`,
        widget: EmbeddedDashboardListing,
        getHidden: settings => !settings["enable-embedding"],
      },
      {
        key: "-embedded-questions",
        display_name: t`Embedded Questions`,
        widget: EmbeddedQuestionListing,
        getHidden: settings => !settings["enable-embedding"],
      },
    ],
  },
  {
    name: t`Caching`,
    slug: "caching",
    settings: [
      {
        key: "enable-query-caching",
        display_name: t`Enable Caching`,
        type: "boolean",
      },
      {
        key: "query-caching-min-ttl",
        display_name: t`Minimum Query Duration`,
        type: "number",
        getHidden: settings => !settings["enable-query-caching"],
        allowValueCollection: true,
      },
      {
        key: "query-caching-ttl-ratio",
        display_name: t`Cache Time-To-Live (TTL) multiplier`,
        type: "number",
        getHidden: settings => !settings["enable-query-caching"],
        allowValueCollection: true,
      },
      {
        key: "query-caching-max-kb",
        display_name: t`Max Cache Entry Size`,
        type: "number",
        getHidden: settings => !settings["enable-query-caching"],
        allowValueCollection: true,
      },
    ],
  },
  /*
    {
        name: "Premium Embedding",
        settings: [
            {
                key: "premium-embedding-token",
                display_name: "Premium Embedding Token",
                widget: PremiumEmbeddingWidget
            }
        ]
    }
    */
];

if (MetabaseSettings.hasPremiumFeature("whitelabel")) {
  SECTIONS.push({
    name: "Whitelabel",
    slug: "whitelabel",
    settings: [
      {
        key: "application-name",
        display_name: "Application Name",
        type: "string",
      },
      {
        key: "application-colors",
        display_name: "Color Palette",
        widget: ColorSchemeWidget,
      },
      {
        key: "application-logo-url",
        display_name: "Logo",
        type: "string",
        widget: LogoUpload,
      },
      {
        key: "application-favicon-url",
        display_name: "Favicon",
        type: "string",
      },
      // {
      //     key: "landing-page",
      //     display_name: "Landing Page",
      //     type: "select",
      //     options: [
      //         { name: "Home Page", value: "" },
      //         { name: "Query Builder", value: "question" },
      //         { name: "Questions", value: "questions" },
      //         { name: "Dashboards", value: "dashboards" }
      //     ]
      // },
      // {
      //     key: "enable-home",
      //     type: "boolean"
      // },
      // {
      //     key: "enable-query-builder",
      //     type: "boolean"
      // },
      // {
      //     key: "enable-saved-questions",
      //     type: "boolean"
      // },
      // {
      //     key: "enable-dashboards",
      //     type: "boolean"
      // },
      // {
      //     key: "enable-pulses",
      //     type: "boolean"
      // },
      // {
      //     key: "enable-dataref",
      //     type: "boolean"
      // },
    ],
  });
}

for (const section of SECTIONS) {
  if (section.slug == null) {
    console.warn("Warning: settings section missing slug:", section.name);
  }
}

export const getSettings = createSelector(
  state => state.settings.settings,
  state => state.admin.settings.warnings,
  (settings, warnings) =>
    settings.map(setting =>
      warnings[setting.key]
        ? { ...setting, warning: warnings[setting.key] }
        : setting,
    ),
);

export const getSettingValues = createSelector(
  getSettings,
  settings => {
    const settingValues = {};
    for (const setting of settings) {
      settingValues[setting.key] = setting.value;
    }
    return settingValues;
  },
);

export const getNewVersionAvailable = createSelector(
  getSettings,
  settings => {
    return MetabaseSettings.newVersionAvailable(settings);
  },
);

export const getSections = createSelector(
  getSettings,
  settings => {
    if (!settings || _.isEmpty(settings)) {
      return [];
    }

    const settingsByKey = _.groupBy(settings, "key");
    return SECTIONS.map(function(section) {
      const sectionSettings = section.settings.map(function(setting) {
        const apiSetting =
          settingsByKey[setting.key] && settingsByKey[setting.key][0];
        if (apiSetting) {
          return {
            placeholder: apiSetting.default,
            ...apiSetting,
            ...setting,
          };
        } else {
          return setting;
        }
      });
      return {
        ...section,
        settings: sectionSettings,
      };
    });
  },
);

export const getActiveSectionName = (state, props) => props.params.section;

export const getActiveSection = createSelector(
  getActiveSectionName,
  getSections,
  (section = "setup", sections) => {
    if (sections) {
      return _.findWhere(sections, { slug: section });
    } else {
      return null;
    }
  },
);
