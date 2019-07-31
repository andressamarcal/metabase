/* @flow */

import { t } from "ttag";
import LineAreaBarChart from "../components/LineAreaBarChart.jsx";
import { lineRenderer } from "../lib/LineAreaBarRenderer";

import {
  GRAPH_DATA_SETTINGS,
  LINE_SETTINGS,
  GRAPH_GOAL_SETTINGS,
  GRAPH_COLORS_SETTINGS,
  GRAPH_AXIS_SETTINGS,
} from "../lib/settings/graph";
import { drillThroughSettings } from "../lib/settings/drill";

export default class LineChart extends LineAreaBarChart {
  static uiName = t`Line`;
  static identifier = "line";
  static iconName = "line";
  static noun = t`line chart`;

  static settings = {
    ...GRAPH_DATA_SETTINGS,
    ...LINE_SETTINGS,
    ...GRAPH_GOAL_SETTINGS,
    ...GRAPH_COLORS_SETTINGS,
    ...GRAPH_AXIS_SETTINGS,
    ...drillThroughSettings(),
  };

  static renderer = lineRenderer;
}
