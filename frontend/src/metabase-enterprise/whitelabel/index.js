import {
  PLUGIN_APP_INIT_FUCTIONS,
  PLUGIN_LANDING_PAGE,
  PLUGIN_LOGO_ICON_COMPONENTS,
  PLUGIN_ADMIN_SETTINGS_UPDATES,
} from "metabase/plugins";

import { t } from "ttag";

import MetabaseSettings from "metabase/lib/settings";

import ColorSchemeWidget from "./components/ColorSchemeWidget";
import LogoUpload from "./components/LogoUpload";
import LogoIcon from "./components/LogoIcon";
import {
  updateColors,
  enabledApplicationNameReplacement,
} from "./lib/whitelabel";

if (MetabaseSettings.hasPremiumFeature("whitelabel")) {
  PLUGIN_LANDING_PAGE.push(() => MetabaseSettings.get("landing_page"));
  PLUGIN_ADMIN_SETTINGS_UPDATES.push(sections => ({
    ...sections,
    whitelabel: {
      name: "Whitelabel",
      settings: [
        {
          key: "application-name",
          display_name: t`Application Name`,
          type: "string",
        },
        {
          key: "application-colors",
          display_name: t`Color Palette`,
          widget: ColorSchemeWidget,
        },
        {
          key: "application-logo-url",
          display_name: t`Logo`,
          type: "string",
          widget: LogoUpload,
        },
        {
          key: "application-favicon-url",
          display_name: t`Favicon`,
          type: "string",
        },
        {
          key: "landing-page",
          display_name: t`Landing Page`,
          type: "string",
          placeholder: "/",
        },
      ],
    },
  }));

  PLUGIN_APP_INIT_FUCTIONS.push(({ root }) => {
    MetabaseSettings.on("application-colors", updateColors);
    MetabaseSettings.on("application-colors", () => {
      root.forceUpdate();
    });
    updateColors();
  });

  enabledApplicationNameReplacement();

  PLUGIN_LOGO_ICON_COMPONENTS.push(LogoIcon);
}
