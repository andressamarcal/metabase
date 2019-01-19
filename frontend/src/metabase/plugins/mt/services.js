/* @flow weak */

import { GET, POST, PUT } from "metabase/lib/api";

export const GTAPApi = {
  list: GET("/api/mt/gtap"),
  create: POST("/api/mt/gtap"),
  update: PUT("/api/mt/gtap/:id"),
  attributes: GET("/api/mt/user/attributes"),
};
