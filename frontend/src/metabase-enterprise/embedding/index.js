import React from "react";
import { jt, t } from "ttag";
import { updateIn } from "icepick";

import MetabaseSettings from "metabase/lib/settings";
import { PLUGIN_ADMIN_SETTINGS_UPDATES } from "metabase/plugins";

import EmbeddingLevel from "metabase/admin/settings/components/widgets/EmbeddingLevel";

if (MetabaseSettings.hasPremiumFeature("embedding")) {
  MetabaseSettings.hideEmbedBranding = () => true;
}

const APP_ORIGIN_SETTING = {
  key: "embedding-app-origin",
  display_name: t`Embedding the entire Metabase app`,
  description: t`If you want to embed all of Metabase, enter the origins of the websites where you want to allow embedding in an iframe.`,
  note: jt`The value must be valid for the ${(
    <code>Content-Security-Policy</code>
  )} header's
    ${(
      <a
        href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy/frame-ancestors"
        className="link"
      >
        <code>frame-ancestors</code> directive
      </a>
    )},
    or empty.`,
  placeholder: "https://*.example.com",
  type: "string",
  getHidden: settings => !settings["enable-embedding"],
};

PLUGIN_ADMIN_SETTINGS_UPDATES.push(sections =>
  updateIn(
    sections,
    ["embedding_in_other_applications", "settings"],
    settings => {
      // remove the embedding level widget from EE
      settings = settings.filter(s => s.widget !== EmbeddingLevel);

      // insert the app origin setting right after the secret key widget
      const itemIndex = settings.findIndex(
        s => s.key === "embedding-secret-key",
      );
      const sliceIndex = itemIndex === -1 ? settings.length : itemIndex + 1;

      settings = [
        ...settings.slice(0, sliceIndex),
        APP_ORIGIN_SETTING,
        ...settings.slice(sliceIndex),
      ];

      return settings;
    },
  ),
);
