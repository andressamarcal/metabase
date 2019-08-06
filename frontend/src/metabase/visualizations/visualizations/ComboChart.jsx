/* @flow */

import { t } from "ttag";
import LineAreaBarChart from "../components/LineAreaBarChart.jsx";
import { comboRenderer } from "../lib/LineAreaBarRenderer";

import {
  GRAPH_DATA_SETTINGS,
  LINE_SETTINGS,
  GRAPH_GOAL_SETTINGS,
  GRAPH_COLORS_SETTINGS,
  GRAPH_AXIS_SETTINGS,
} from "../lib/settings/graph";
import { drillThroughSettings } from "../lib/settings/drill";

export default class LineChart extends LineAreaBarChart {
  static uiName = t`Line + Bar`;
  static identifier = "combo";
  static iconName = "lineandbar";
  static noun = t`line and bar chart`;

  static settings = {
    ...GRAPH_DATA_SETTINGS,
    ...LINE_SETTINGS,
    ...GRAPH_GOAL_SETTINGS,
    ...GRAPH_COLORS_SETTINGS,
    ...GRAPH_AXIS_SETTINGS,
    ...drillThroughSettings(),
  };

  static renderer = comboRenderer;
}
