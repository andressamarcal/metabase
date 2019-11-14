import { t } from "ttag";
import { updateIn } from "icepick";

import MetabaseSettings from "metabase/lib/settings";
import {
  PLUGIN_AUTH_PROVIDERS,
  PLUGIN_SHOW_CHANGE_PASSWORD_CONDITIONS,
  PLUGIN_ADMIN_SETTINGS_UPDATES,
} from "metabase/plugins";
import { UtilApi } from "metabase/services";

import AuthenticationOption from "metabase/admin/settings/components/widgets/AuthenticationOption";
import GroupMappingsWidget from "metabase/admin/settings/components/widgets/GroupMappingsWidget";
import SecretKeyWidget from "metabase/admin/settings/components/widgets/SecretKeyWidget";

import SettingsSAMLForm from "./components/SettingsSAMLForm";
import SettingsJWTForm from "./components/SettingsJWTForm";

import SSOButton from "./components/SSOButton";

PLUGIN_ADMIN_SETTINGS_UPDATES.push(sections =>
  updateIn(sections, ["authentication", "settings"], settings => [
    ...settings,
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
  ]),
);

PLUGIN_ADMIN_SETTINGS_UPDATES.push(sections => ({
  ...sections,
  "authentication/saml": {
    sidebar: false,
    component: SettingsSAMLForm,
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
  "authentication/jwt": {
    sidebar: false,
    component: SettingsJWTForm,
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
}));

const SSO_PROVIDER = {
  name: "sso",
  Button: SSOButton,
};

PLUGIN_AUTH_PROVIDERS.push(providers => {
  if (MetabaseSettings.get("other_sso_configured")) {
    providers = [SSO_PROVIDER, ...providers];
  }
  if (!MetabaseSettings.get("enable_password_login")) {
    providers = providers.filter(p => p.name !== "password");
  }
  return providers;
});

PLUGIN_SHOW_CHANGE_PASSWORD_CONDITIONS.push(
  user =>
    !user.google_auth &&
    !user.ldap_auth &&
    MetabaseSettings.get("enable_password_login"),
);
