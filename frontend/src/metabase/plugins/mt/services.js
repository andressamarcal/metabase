/* @flow weak */

import api from "metabase/lib/api";
const { GET, POST, PUT } = api;

export const GTAPApi = {
  list: GET("/api/mt/gtap"),
  create: POST("/api/mt/gtap"),
  update: PUT("/api/mt/gtap/:id"),
  attributes: GET("/api/mt/user/attributes"),
};
