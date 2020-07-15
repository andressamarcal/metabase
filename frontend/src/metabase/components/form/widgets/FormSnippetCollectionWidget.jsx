import React from "react";

import CollectionSelect from "metabase/containers/CollectionSelect";
import SnippetCollections from "metabase/entities/snippet-collections";

const FormSnippetCollectionWidget = ({ field }) => (
  <CollectionSelect entity={SnippetCollections} {...field} />
);

export default FormSnippetCollectionWidget;
