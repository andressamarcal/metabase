import _ from "underscore";
import inflection from "inflection";
import { t } from "ttag";
import MetabaseUtils from "metabase/lib/utils";

const mb_settings = _.clone(window.MetabaseBootstrap);

const settingListeners = {};

// provides access to Metabase application settings
const MetabaseSettings = {
  get: function(propName, defaultValue = null) {
    return mb_settings[propName] !== undefined
      ? mb_settings[propName]
      : defaultValue;
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
    return (
      mb_settings.setup_token !== undefined && mb_settings.setup_token !== null
    );
  },

  googleAuthEnabled: function() {
    return mb_settings.google_auth_client_id != null;
  },

  ldapEnabled: function() {
    return mb_settings.ldap_configured;
  },

  otherSSOEnabled() {
    return mb_settings.other_sso_configured;
  },

  passwordEnabled() {
    return mb_settings.enable_password_login;
  },

  colorScheme: function() {
    // FIXME: Ugh? initially load public setting as "application_color" but if the admin updates it
    // we need to use "application-colors"
    return mb_settings["application-colors"] || mb_settings.application_colors;
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
    return Object.entries(mb_settings["features"])
      .filter(([key, enabled]) => enabled)
      .map(([key]) => key);
  },

  hasPremiumFeature: feature => {
    const hasFeature =
      mb_settings.premium_features && mb_settings.premium_features[feature];
    if (hasFeature == undefined) {
      console.warn("Unknown premium feature", feature);
    }
    return hasFeature;
  },

  metastoreUrl: () => mb_settings.metastore_url,

  docsUrl: (page = "", anchor = "") => {
    // let { tag } = MetabaseSettings.get("version", {});
    // if (!tag) {
    //   tag = "latest";
    // }

    // NOTE: temporarily use "latest" for Enterprise Edition
    const tag = "latest";

    if (page) {
      page = `/${page}.html`;
    }
    if (anchor) {
      anchor = `#${anchor}`;
    }
    return `https://metabase.com/docs/${tag}${page}${anchor}`;
  },

  newVersionAvailable: function(settings) {
    let versionInfo = _.findWhere(settings, { key: "version-info" });
    const currentVersion = MetabaseSettings.get("version").tag;

    if (versionInfo) {
      versionInfo = versionInfo.value;
    }

    return (
      versionInfo &&
      versionInfo.latest_enterprise &&
      MetabaseUtils.compareVersions(
        currentVersion,
        versionInfo.latest_enterprise.version,
      ) < 0
    );
  },

  // returns a map that looks like {total: 6, digit: 1}
  passwordComplexityRequirements: () => mb_settings.password_complexity,

  // returns a description of password complexity requirements rather than the actual map of requirements
  passwordComplexityDescription: function(capitalize) {
    const complexity = this.get("password_complexity");

    const clauseDescription = function(clause) {
      switch (clause) {
        case "lower":
          return t`lower case letter`;
        case "upper":
          return t`upper case letter`;
        case "digit":
          return t`number`;
        case "special":
          return t`special character`;
      }
    };

    const description =
      capitalize === false
        ? t`must be` + " " + complexity.total + " " + t`characters long`
        : t`Must be` + " " + complexity.total + " " + t`characters long`;
    const clauses = [];

    ["lower", "upper", "digit", "special"].forEach(function(clause) {
      if (clause in complexity) {
        const desc =
          complexity[clause] > 1
            ? inflection.pluralize(clauseDescription(clause))
            : clauseDescription(clause);
        clauses.push(
          MetabaseUtils.numberToWord(complexity[clause]) + " " + desc,
        );
      }
    });

    if (clauses.length > 0) {
      return description + " " + t`and include` + " " + clauses.join(", ");
    } else {
      return description;
    }
  },

  on: function(setting, callback) {
    settingListeners[setting] = settingListeners[setting] || [];
    settingListeners[setting].push(callback);
  },
};

export default MetabaseSettings;
