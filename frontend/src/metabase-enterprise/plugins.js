import MetabaseSettings from "metabase/lib/settings";

// SETTINGS OVERRIDES:

// NOTE: temporarily use "latest" for Enterprise Edition docs
MetabaseSettings.docsTag = () => "latest";
// NOTE: use the "enterprise" key from version-info.json
MetabaseSettings.versionInfo = () =>
  MetabaseSettings.get("version-info", {}).enterprise || {};

// PLUGINS:

// import "./management";

// Custom drill through updates visualization settings. It needs to be imported
// before any features that import "metabase/visualizations".
import "./custom_drill_through";
import "./audit_app";
import "./sandboxes";
import "./auth";
import "./whitelabel";
import "./embedding";
import "./store";
