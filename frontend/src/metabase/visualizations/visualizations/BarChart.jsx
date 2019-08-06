/* @flow */

import { t } from "ttag";
import LineAreaBarChart from "../components/LineAreaBarChart.jsx";
import { barRenderer } from "../lib/LineAreaBarRenderer";

import {
  GRAPH_DATA_SETTINGS,
  STACKABLE_SETTINGS,
  GRAPH_GOAL_SETTINGS,
  GRAPH_COLORS_SETTINGS,
  GRAPH_AXIS_SETTINGS,
} from "../lib/settings/graph";
import { drillThroughSettings } from "../lib/settings/drill";

export default class BarChart extends LineAreaBarChart {
  static uiName = t`Bar`;
  static identifier = "bar";
  static iconName = "bar";
  static noun = t`bar chart`;

  static settings = {
    ...GRAPH_DATA_SETTINGS,
    ...STACKABLE_SETTINGS,
    ...GRAPH_GOAL_SETTINGS,
    ...GRAPH_COLORS_SETTINGS,
    ...GRAPH_AXIS_SETTINGS,
    ...drillThroughSettings(),
  };

  static renderer = barRenderer;
}
