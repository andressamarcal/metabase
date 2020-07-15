import React from "react";

import ItemSelect from "metabase/containers/ItemSelect";
import CollectionPicker from "metabase/containers/CollectionPicker";
import SnippetCollections from "metabase/entities/snippet-collections";

const CollectionSelect = ItemSelect(
  CollectionPicker,
  SnippetCollections.Name,
  "collection",
);

const FormSnippetCollectionWidget = ({ field }) => (
  <CollectionSelect
    entity={SnippetCollections}
    {...field}
    value={field.value || "root"} // needed so SnippetCollections.Name finds the right collection
  />
);
export default FormSnippetCollectionWidget;
