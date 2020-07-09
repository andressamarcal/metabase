import React from "react";
import { t } from "ttag";

import Select from "metabase/components/Select";

import SnippetCollections from "metabase/entities/snippet-collections";
import { canonicalCollectionId } from "metabase/entities/collections";

const FormSnippetCollectionWidget = SnippetCollections.loadList()(
  ({ field, values, snippetCollections: options }) => {
    if (values.id !== undefined) {
      const collection = options.find(sc => sc.id === values.id);
      const descendentPrefix = collection.location + collection.id;
      options = options.filter(
        sc =>
          // you can't nest a collection in itself
          sc.id !== collection.id &&
          // or in any of its descendents
          !(sc.location || "").startsWith(descendentPrefix),
      );
    }
    return (
      <Select
        {...field}
        value={canonicalCollectionId(field.value)}
        // react-redux expects to be raw value
        onChange={e => field.onChange(e.target.value)}
        options={options}
        optionNameFn={o => (o.id === "root" ? t`Top Folder` : o.name)}
        optionValueFn={o => canonicalCollectionId(o.id)}
      />
    );
  },
);

export default FormSnippetCollectionWidget;
