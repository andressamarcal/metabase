import {
  PLUGIN_DRILLS,
  PLUGIN_CHART_SETTINGS,
  PLUGIN_TABLE_COLUMN_SETTINGS,
} from "metabase/plugins";

import CustomLink from "./components/CustomLink";
import { getColumnSettings } from "./settings/table";
import { drillThroughSettings } from "./settings/drill";

PLUGIN_DRILLS.push(CustomLink);
PLUGIN_TABLE_COLUMN_SETTINGS.push(getColumnSettings);
Object.assign(PLUGIN_CHART_SETTINGS, drillThroughSettings());
