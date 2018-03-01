import _ from "underscore";
import inflection from 'inflection';
import { t } from 'c-3po';
import MetabaseUtils from "metabase/lib/utils";

const mb_settings = _.clone(window.MetabaseBootstrap);

const settingListeners = {};

// provides access to Metabase application settings
const MetabaseSettings = {

    get: function(propName, defaultValue = null) {
        return mb_settings[propName] !== undefined ? mb_settings[propName] : defaultValue;
    },

    set: function(key, value) {
        if (mb_settings[key] !== value) {
            mb_settings[key] = value;
            if (settingListeners[key]) {
                for (const listener of settingListeners[key]) {
                    setTimeout(() => listener(value));
                }
            }
        }
    },

    setAll: function(settings) {
        for (const key in settings) {
            MetabaseSettings.set(key, settings[key]);
        }
    },

    // these are all special accessors which provide a lookup of a property plus some additional help
    adminEmail: function() {
        return mb_settings.admin_email;
    },

    isEmailConfigured: function() {
        return mb_settings.email_configured;
    },

    isTrackingEnabled: function() {
        return mb_settings.anon_tracking_enabled || false;
    },

    hasSetupToken: function() {
        return (mb_settings.setup_token !== undefined && mb_settings.setup_token !== null);
    },

    ssoEnabled: function() {
        return mb_settings.google_auth_client_id != null;
    },

    ldapEnabled: function() {
        return mb_settings.ldap_configured;
    },

    colorScheme: function() {
        // FIXME: Ugh? initially load public setting as "application_color" but if the admin updates it
        // we need to use "application-color"
        return mb_settings["application-color"] || mb_settings.application_color;
    },

    applicationName: function() {
        // FIXME: Ugh? see comment in colorScheme()
        return mb_settings["application-name"] || mb_settings.application_name;
    },

    landingPage: function() {
        // FIXME: Ugh? see comment in colorScheme()
        const features = MetabaseSettings.features();
        let page;
        if (features.length === 1) {
            // NOTE: assumes features are named same as their URL
            page = features[0];
        } else {
            page = mb_settings["landing-page"] || mb_settings.landing_page;
        }
        return "/" + (page || "");
    },

    features: function() {
        return Object.entries(mb_settings["features"]).filter(([key, enabled]) => enabled).map(([key]) => key);
    },
    
    hideEmbedBranding: () => mb_settings.hide_embed_branding,

    metastoreUrl: () => mb_settings.metastore_url,

    newVersionAvailable: function(settings) {
        let versionInfo = _.findWhere(settings, {key: "version-info"}),
            currentVersion = MetabaseSettings.get("version").tag;

        if (versionInfo) versionInfo = versionInfo.value;

        return (versionInfo && MetabaseUtils.compareVersions(currentVersion, versionInfo.latest.version) < 0);
    },

    passwordComplexity: function(capitalize) {
        const complexity = this.get('password_complexity');

        const clauseDescription = function(clause) {
            switch (clause) {
                case "lower": return t`lower case letter`;
                case "upper": return t`upper case letter`;
                case "digit": return t`number`;
                case "special": return t`special character`;
            }
        };

        let description = (capitalize === false) ? t`must be` + " " + complexity.total + " " + t`characters long` : t`Must be` + " " + complexity.total + " " + t`characters long`,
            clauses = [];

        ["lower", "upper", "digit", "special"].forEach(function(clause) {
            if (clause in complexity) {
                let desc = (complexity[clause] > 1) ? inflection.pluralize(clauseDescription(clause)) : clauseDescription(clause);
                clauses.push(MetabaseUtils.numberToWord(complexity[clause])+" "+desc);
            }
        });

        if (clauses.length > 0) {
            return description+" "+ t`and include` +" "+clauses.join(", ");
        } else {
            return description;
        }
    },

    on: function(setting, callback) {
        settingListeners[setting] = settingListeners[setting] || [];
        settingListeners[setting].push(callback);
    }
}

export default MetabaseSettings;
