import React from "react";
import cx from "classnames";

import Icon from "metabase/components/Icon";

import CollectionOptionsButton from "./CollectionOptionsButton";

const ICON_SIZE = 16;

export default class CollectionRow extends React.Component {
  render() {
    const { item: collection, setSnippetCollectionId } = this.props;
    const onSelectCollection = () => setSnippetCollectionId(collection.id);

    return (
      <div
        className={cx(
          { "bg-light-hover cursor-pointer": !collection.archived },
          "hover-parent hover--visibility flex align-center p2 text-brand",
        )}
        {...(collection.archived ? undefined : { onClick: onSelectCollection })}
      >
        <Icon name="folder" size={ICON_SIZE} style={{ opacity: 0.25 }} />
        <span className="flex-full ml1 text-bold">{collection.name}</span>
        <CollectionOptionsButton {...this.props} collection={collection} />
      </div>
    );
  }
}
