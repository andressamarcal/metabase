/* @flow */

import { createEntity } from "metabase/lib/entities";

import _ from "underscore";

const Groups = createEntity({
  name: "groups",
  path: "/api/permissions/group",
  api: {
    // TODO: replace fake `get`
    get: async ({ id }) => {
      const group = _.findWhere(await Groups.api.list(), { id: parseInt(id) });
      if (group) {
        return group;
      } else {
        throw new Error(`Group ${id} not found`);
      }
    },
  },
});

export default Groups;
