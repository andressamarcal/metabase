import _ from "underscore";
import { createSelector } from "reselect";
import MetabaseSettings from "metabase/lib/settings";
import { t } from 'c-3po';
import { slugify } from "metabase/lib/formatting";
import CustomGeoJSONWidget from "./components/widgets/CustomGeoJSONWidget.jsx";
import {
    PublicLinksDashboardListing,
    PublicLinksQuestionListing,
    EmbeddedQuestionListing,
    EmbeddedDashboardListing
} from "./components/widgets/PublicLinksListing.jsx";
import SecretKeyWidget from "./components/widgets/SecretKeyWidget.jsx";
import EmbeddingLegalese from "./components/widgets/EmbeddingLegalese";
import EmbeddingLevel from "./components/widgets/EmbeddingLevel";
import LdapGroupMappingsWidget from "./components/widgets/LdapGroupMappingsWidget";
import LogoUpload from "./components/widgets/LogoUpload";

import { UtilApi } from "metabase/services";

const SECTIONS = [
    {
        name: t`Setup`,
        settings: []
    },
    {
        name: t`General`,
        settings: [
            {
                key: "site-name",
                display_name: t`Site Name`,
                type: "string"
            },
            {
                key: "site-url",
                display_name: t`Site URL`,
                type: "string"
            },
            {
                key: "admin-email",
                display_name: t`Email Address for Help Requests`,
                type: "string"
            },
            {
                key: "report-timezone",
                display_name: t`Report Timezone`,
                type: "select",
                options: [
                    { name: t`Database Default`, value: "" },
                    ...MetabaseSettings.get('timezones')
                ],
                placeholder: t`Select a timezone`,
                note: t`Not all databases support timezones, in which case this setting won't take effect.`,
                allowValueCollection: true
            },
            {
                key: "site-locale",
                display_name: t`Language`,
                type: "select",
                options:  (MetabaseSettings.get("available_locales") || []).map(([value, name]) => ({ name, value })),
                placeholder: t`Select a language`,
                getHidden: () => MetabaseSettings.get("available_locales").length < 2
            },
            {
                key: "anon-tracking-enabled",
                display_name: t`Anonymous Tracking`,
                type: "boolean"
            },
            {
                key: "humanization-strategy",
                display_name: t`Friendly Table and Field Names`,
                type: "select",
                options: [
                    { value: "advanced", name: t`Enabled` },
                    { value: "simple",   name: t`Only replace underscores and dashes with spaces` },
                    { value: "none",     name: t`Disabled` }
                ],
                // this needs to be here because 'advanced' is the default value, so if you select 'advanced' the
                // widget will always show the placeholder instead of the 'name' defined above :(
                placeholder: t`Enabled`
            },
            {
                key: "enable-nested-queries",
                display_name: t`Enable Nested Queries`,
                type: "boolean"
            }
        ]
    },
    {
        name: t`Updates`,
        settings: [
            {
                key: "check-for-updates",
                display_name: t`Check for updates`,
                type: "boolean"
            }
        ]
    },
    {
        name: t`Email`,
        settings: [
            {
                key: "email-smtp-host",
                display_name: t`SMTP Host`,
                placeholder: "smtp.yourservice.com",
                type: "string",
                required: true,
                autoFocus: true
            },
            {
                key: "email-smtp-port",
                display_name: t`SMTP Port`,
                placeholder: "587",
                type: "number",
                required: true,
                validations: [["integer", t`That's not a valid port number`]]
            },
            {
                key: "email-smtp-security",
                display_name: t`SMTP Security`,
                description: null,
                type: "radio",
                options: { none: "None", ssl: "SSL", tls: "TLS", starttls: "STARTTLS" },
                defaultValue: 'none'
            },
            {
                key: "email-smtp-username",
                display_name: t`SMTP Username`,
                description: null,
                placeholder: "youlooknicetoday",
                type: "string"
            },
            {
                key: "email-smtp-password",
                display_name: t`SMTP Password`,
                description: null,
                placeholder: "Shh...",
                type: "password"
            },
            {
                key: "email-from-address",
                display_name: t`From Address`,
                placeholder: "metabase@yourcompany.com",
                type: "string",
                required: true,
                validations: [["email", t`That's not a valid email address`]]
            }
        ]
    },
    {
        name: "Slack",
        settings: [
            {
                key: "slack-token",
                display_name: t`Slack API Token`,
                description: "",
                placeholder: t`Enter the token you received from Slack`,
                type: "string",
                required: false,
                autoFocus: true
            },
            {
                key: "metabot-enabled",
                display_name: "MetaBot",
                type: "boolean",
                // TODO: why do we have "defaultValue" here in addition to the "default" specified by the backend?
                defaultValue: false,
                required: true,
                autoFocus: false
            },
        ]
    },
    {
        name: t`Single Sign-On`,
        sidebar: false,
        settings: [
            {
                key: "google-auth-client-id"
            },
            {
                key: "google-auth-auto-create-accounts-domain"
            }
        ]
    },
    {
        name: t`Authentication`,
        settings: []
    },
    {
        name: t`LDAP`,
        sidebar: false,
        settings: [
            {
                key: "ldap-enabled",
                display_name: t`LDAP Authentication`,
                description: null,
                type: "boolean"
            },
            {
                key: "ldap-host",
                display_name: t`LDAP Host`,
                placeholder: "ldap.yourdomain.org",
                type: "string",
                required: true,
                autoFocus: true
            },
            {
                key: "ldap-port",
                display_name: t`LDAP Port`,
                placeholder: "389",
                type: "string",
                validations: [["integer", t`That's not a valid port number`]]
            },
            {
                key: "ldap-security",
                display_name: t`LDAP Security`,
                description: null,
                type: "radio",
                options: { none: "None", ssl: "SSL", starttls: "StartTLS" },
                defaultValue: "none"
            },
            {
                key: "ldap-bind-dn",
                display_name: t`Username or DN`,
                type: "string"
            },
            {
                key: "ldap-password",
                display_name: t`Password`,
                type: "password"
            },
            {
                key: "ldap-user-base",
                display_name: t`User search base`,
                type: "string",
                required: true
            },
            {
                key: "ldap-user-filter",
                display_name: t`User filter`,
                type: "string",
                validations: [["ldap_filter", t`Check your parentheses`]]
            },
            {
                key: "ldap-attribute-email",
                display_name: t`Email attribute`,
                type: "string"
            },
            {
                key: "ldap-attribute-firstname",
                display_name: t`First name attribute`,
                type: "string"
            },
            {
                key: "ldap-attribute-lastname",
                display_name: t`Last name attribute`,
                type: "string"
            },
            {
                key: "ldap-group-sync",
                display_name: t`Synchronize group memberships`,
                description: null,
                widget: LdapGroupMappingsWidget
            },
            {
                key: "ldap-group-base",
                display_name:t`"Group search base`,
                type: "string"
            },
            {
                key: "ldap-group-mappings"
            }
        ]
    },
    {
        name: t`Maps`,
        settings: [
            {
                key: "map-tile-server-url",
                display_name: t`Map tile server URL`,
                note: t`${t`Metabase`} uses OpenStreetMaps by default.`,
                type: "string"
            },
            {
                key: "custom-geojson",
                display_name: t`Custom Maps`,
                description: t`Add your own GeoJSON files to enable different region map visualizations`,
                widget: CustomGeoJSONWidget,
                noHeader: true
            }
        ]
    },
    {
        name: t`Public Sharing`,
        settings: [
            {
                key: "enable-public-sharing",
                display_name: t`Enable Public Sharing`,
                type: "boolean"
            },
            {
                key: "-public-sharing-dashboards",
                display_name: t`Shared Dashboards`,
                widget: PublicLinksDashboardListing,
                getHidden: (settings) => !settings["enable-public-sharing"]
            },
            {
                key: "-public-sharing-questions",
                display_name: t`Shared Questions`,
                widget: PublicLinksQuestionListing,
                getHidden: (settings) => !settings["enable-public-sharing"]
            }
        ]
    },
    {
        name: t`Embedding in other Applications`,
        settings: [
            {
                key: "enable-embedding",
                description: null,
                widget: EmbeddingLegalese,
                getHidden: (settings) => settings["enable-embedding"],
                onChanged: async (oldValue, newValue, settingsValues, onChangeSetting) => {
                    // Generate a secret key if none already exists
                    if (!oldValue && newValue && !settingsValues["embedding-secret-key"]) {
                        let result = await UtilApi.random_token();
                        await onChangeSetting("embedding-secret-key", result.token);
                    }
                }
            }, {
                key: "enable-embedding",
                display_name: t`Enable Embedding ${t`Metabase`} in other Applications`,
                type: "boolean",
                getHidden: (settings) => !settings["enable-embedding"]
            },
            {
                widget: EmbeddingLevel,
                getHidden: (settings) => !settings["enable-embedding"]
            },
            {
                key: "embedding-secret-key",
                display_name: t`Embedding secret key`,
                widget: SecretKeyWidget,
                getHidden: (settings) => !settings["enable-embedding"]
            },
            {
                key: "-embedded-dashboards",
                display_name: t`Embedded Dashboards`,
                widget: EmbeddedDashboardListing,
                getHidden: (settings) => !settings["enable-embedding"]
            },
            {
                key: "-embedded-questions",
                display_name: t`Embedded Questions`,
                widget: EmbeddedQuestionListing,
                getHidden: (settings) => !settings["enable-embedding"]
            }
        ]
    },
    {
        name: t`Caching`,
        settings: [
            {
                key: "enable-query-caching",
                display_name: t`Enable Caching`,
                type: "boolean"
            },
            {
                key: "query-caching-min-ttl",
                display_name: t`Minimum Query Duration`,
                type: "number",
                getHidden: (settings) => !settings["enable-query-caching"],
                allowValueCollection: true
            },
            {
                key: "query-caching-ttl-ratio",
                display_name: t`Cache Time-To-Live (TTL) multiplier`,
                type: "number",
                getHidden: (settings) => !settings["enable-query-caching"],
                allowValueCollection: true
            },
            {
                key: "query-caching-max-kb",
                display_name: t`Max Cache Entry Size`,
                type: "number",
                getHidden: (settings) => !settings["enable-query-caching"],
                allowValueCollection: true
            }
        ]
    },
    {
        name: "Whitelabel",
        settings: [
            {
                key: "application-name",
                display_name: "Application Name",
                type: "string",
            },
            {
                key: "application-color",
                display_name: "Primary Color",
                type: "string",
            },
            {
                key: "application-logo-url",
                display_name: "Logo",
                type: "string",
                widget: LogoUpload,
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
        ]
    },
    {
        name: t`X-Rays`,
        settings: [
            {
                key: "enable-xrays",
                display_name: t`Enable X-Rays`,
                type: "boolean",
                allowValueCollection: true
            },
            {
                key: "xray-max-cost",
                type: "string",
                allowValueCollection: true

            }
        ]
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
for (const section of SECTIONS) {
    section.slug = slugify(section.name);
}

export const getSettings = createSelector(
    state => state.settings.settings,
    state => state.admin.settings.warnings,
    (settings, warnings) =>
        settings.map(setting => warnings[setting.key] ?
            { ...setting, warning: warnings[setting.key] } :
            setting
        )
)

export const getSettingValues = createSelector(
    getSettings,
    (settings) => {
        const settingValues = {};
        for (const setting of settings) {
            settingValues[setting.key] = setting.value;
        }
        return settingValues;
    }
)

export const getNewVersionAvailable = createSelector(
    getSettings,
    (settings) => {
        return MetabaseSettings.newVersionAvailable(settings);
    }
);

export const getSections = createSelector(
    getSettings,
    (settings) => {
        if (!settings || _.isEmpty(settings)) {
            return [];
        }

        let settingsByKey = _.groupBy(settings, 'key');
        return SECTIONS.map(function(section) {
            let sectionSettings = section.settings.map(function(setting) {
                const apiSetting = settingsByKey[setting.key] && settingsByKey[setting.key][0];
                if (apiSetting) {
                    return {
                        placeholder: apiSetting.default,
                        ...apiSetting,
                        ...setting
                    };
                } else {
                    return setting;
                }
            });
            return {
                ...section,
                settings: sectionSettings
            };
        });
    }
);

export const getActiveSectionName = (state, props) => props.params.section

export const getActiveSection = createSelector(
    getActiveSectionName,
    getSections,
    (section = "setup", sections) => {
        if (sections) {
            return _.findWhere(sections, { slug: section });
        } else {
            return null;
        }
    }
);
