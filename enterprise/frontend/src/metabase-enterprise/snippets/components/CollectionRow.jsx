import React from "react";
import { t } from "ttag";

import Icon from "metabase/components/Icon";
import PopoverWithTrigger from "metabase/components/PopoverWithTrigger";
import AccordionList from "metabase/components/AccordionList";

const ICON_SIZE = 16;

export default class CollectionRow extends React.Component {
  render() {
    const {
      item: collection,
      setSnippetCollectionId,
      setSidebarState,
      user,
    } = this.props;
    const onSelectCollection = () => setSnippetCollectionId(collection.id);
    const onEdit = collection =>
      setSidebarState({ modalSnippetCollection: collection });
    const onEditCollectionPermissions = () =>
      setSidebarState({ permissionsModalCollectionId: collection.id });

    return (
      <div
        className="hover-parent hover--visibility flex align-center p2 text-brand bg-light-hover cursor-pointer"
        onClick={onSelectCollection}
      >
        <Icon name="folder" size={ICON_SIZE} style={{ opacity: 0.25 }} />
        <span className="flex-full ml1 text-bold">{collection.name}</span>
        {collection.can_write && (
          <div
            // prevent the ellipsis click from selecting the folder also
            onClick={e => e.stopPropagation()}
            // cap the large ellipsis so it doesn't increase the row height
            style={{ height: ICON_SIZE }}
          >
            <PopoverWithTrigger
              triggerElement={
                <Icon name="ellipsis" size={20} className="hover-child" />
              }
            >
              {({ onClose }) => (
                <AccordionList
                  className="text-brand"
                  sections={[
                    {
                      items: [
                        {
                          name: t`Edit`,
                          onClick: () => onEdit(collection),
                        },
                        ...(user.is_superuser
                          ? [
                              {
                                name: t`Change permissions`,
                                onClick: onEditCollectionPermissions,
                              },
                            ]
                          : []),
                        {
                          name: t`Archive`,
                          onClick: () => collection.setArchived(true),
                        },
                      ],
                    },
                  ]}
                  onChange={item => {
                    item.onClick();
                    onClose();
                  }}
                />
              )}
            </PopoverWithTrigger>
          </div>
        )}
      </div>
    );
  }
}
