/* @flow */

import { createEntity } from "metabase/lib/entities";

import _ from "underscore";

import type { GroupId } from "metabase/meta/types/Permissions";

const Groups = createEntity({
  name: "groups",
  path: "/api/permissions/group",

  form: {
    fields: [{ name: "name" }],
  },
});

export default Groups;
