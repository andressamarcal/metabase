/* @flow */
import _ from "underscore";
import { t } from "ttag";

import { color } from "metabase/lib/colors";
import { createEntity, undo } from "metabase/lib/entities";
import { CollectionSchema } from "metabase/schema";
import NormalCollections, {
  canonicalCollectionId,
} from "metabase/entities/collections";

const Collections = createEntity({
  name: "snippetCollections",
  schema: CollectionSchema,

  api: _.mapObject(NormalCollections.api, f => (first, ...rest) =>
    f({ ...first, namespace: "snippets" }, ...rest),
  ),

  displayNameOne: t`snippet collection`,
  displayNameMany: t`snippet collections`,

  objectActions: {
    setArchived: ({ id }, archived, opts) =>
      Collections.actions.update(
        { id },
        { archived },
        undo(opts, "snippetCollection", archived ? "archived" : "unarchived"),
      ),

    setCollection: ({ id }, collection, opts) =>
      Collections.actions.update(
        { id },
        { parent_id: canonicalCollectionId(collection && collection.id) },
        undo(opts, "snippetCollection", "moved"),
      ),

    // NOTE: DELETE not currently implemented
    // $FlowFixMe: no official way to disable builtin actions yet
    delete: null,
  },

  form: {
    fields: [
      {
        name: "name",
        title: t`Give your folder a name`,
        placeholder: t`Something short but sweet`,
        validate: name =>
          (!name && t`Name is required`) ||
          (name && name.length > 100 && t`Name must be 100 characters or less`),
      },
      {
        name: "description",
        title: t`Add a description`,
        type: "text",
        placeholder: t`It's optional but oh, so helpful`,
        normalize: description => description || null, // expected to be nil or non-empty string
      },
      {
        name: "color",
        title: t`Color`,
        type: "hidden",
        initial: () => color("brand"),
        validate: color => !color && t`Color is required`,
      },
      {
        name: "parent_id",
        title: t`Folder this should be in`,
        type: "snippetCollection",
        normalize: canonicalCollectionId,
      },
    ],
  },

  getAnalyticsMetadata([object], { action }, getState) {
    return undefined; // TODO: is there anything informative to track here?
  },
});

export default Collections;
