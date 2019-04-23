/* @flow */

import type { QueryMode } from "metabase/meta/types/Visualization";

import CustomLink from "../drill/CustomLink";

const PublicMode: QueryMode = {
  name: "public",
  actions: [],
  drills: [CustomLink],
};

export default PublicMode;
