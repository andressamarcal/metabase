/* @flow weak */

import React from "react";
import PropTypes from "prop-types";

import { t } from "ttag";
import cx from "classnames";
import _ from "underscore";

import Icon from "metabase/components/Icon";
import Button from "metabase/components/Button";
import Modal from "metabase/components/Modal";
import PopoverWithTrigger from "metabase/components/PopoverWithTrigger";
import AccordionList from "metabase/components/AccordionList";
import SidebarContent from "metabase/query_builder/components/SidebarContent";
import SidebarHeader from "metabase/query_builder/components/SidebarHeader";
import { color } from "metabase/lib/colors";

import Snippets from "metabase/entities/snippets";
import SnippetCollections from "metabase/entities/snippet-collections";
import Search from "metabase/entities/search";

import type { Snippet } from "metabase/meta/types/Snippet";

type Props = {
  onClose: () => void,
  setModalSnippet: () => void,
  openSnippetModalWithSelectedText: () => void,
  insertSnippet: () => void,
  snippets: Snippet[],
};

type State = {
  showSearch: boolean,
  searchString: string,
  showArchived: boolean,
};

const ICON_SIZE = 16;
const HEADER_ICON_SIZE = 18;
const MIN_SNIPPETS_FOR_SEARCH = 15;

@SnippetCollections.load({
  id: (state, props) =>
    props.snippetCollectionId === null ? "root" : props.snippetCollectionId,
})
@Snippets.loadList({ wrapped: true })
@Search.loadList({
  query: (state, props) => ({
    collection:
      props.snippetCollectionId === null ? "root" : props.snippetCollectionId,
    namespace: "snippets",
  }),
  wrapped: true,
})
export default class SnippetSidebar extends React.Component {
  props: Props;
  state: State = {
    showSearch: false,
    searchString: "",
    showArchived: false,
  };
  searchBox: ?HTMLInputElement;

  static propTypes = {
    onClose: PropTypes.func.isRequired,
    setModalSnippet: PropTypes.func.isRequired,
    openSnippetModalWithSelectedText: PropTypes.func.isRequired,
    insertSnippet: PropTypes.func.isRequired,
  };

  showSearch = () => {
    this.setState({ showSearch: true });
    this.searchBox && this.searchBox.focus();
  };
  hideSearch = () => {
    this.setState({ showSearch: false, searchString: "" });
  };

  footer = () => (
    <div
      className="p2 flex text-small text-medium cursor-pointer text-brand-hover hover-parent hover--display"
      onClick={() => this.setState({ showArchived: true })}
    >
      <Icon
        className="mr1 text-light hover-child--hidden"
        name="archive"
        size={ICON_SIZE}
      />
      <Icon
        className="mr1 text-brand hover-child"
        name="archive"
        size={ICON_SIZE}
      />
      {t`Archived snippets`}
    </div>
  );

  render() {
    const {
      snippets,
      openSnippetModalWithSelectedText,
      snippetCollection,
      search,
    } = this.props;
    const { collectionId, showSearch, searchString, showArchived } = this.state;
    const snippetsById = _.indexBy(snippets, "id");
    const items = _.sortBy(search, "model"); // relies on "collection" sorting before "snippet"

    const filteredSnippets = items.filter(item =>
      item.model === "snippet"
        ? snippetsById[item.id] &&
          (!showSearch ||
            item.name.toLowerCase().includes(searchString.toLowerCase()))
        : !showSearch,
    );

    if (showArchived) {
      return (
        <ArchivedSnippets
          onBack={() => this.setState({ showArchived: false })}
        />
      );
    }

    return (
      <SidebarContent footer={this.footer()}>
        {items.length === 0 && snippetCollection.id === "root" ? (
          <div className="px3 flex flex-column align-center">
            <svg
              viewBox="0 0 10 10"
              className="mb2"
              style={{ width: "25%", marginTop: 120 }}
            >
              <path
                style={{ stroke: color("bg-medium"), strokeWidth: 1 }}
                d="M0,1H8M0,3H10M0,5H7M0,7H10M0,9H3"
              />
            </svg>
            <h4 className="text-medium">{t`Snippets are reusable bits of SQL`}</h4>
            <button
              onClick={openSnippetModalWithSelectedText}
              className="Button Button--primary"
              style={{ marginTop: 80 }}
            >{t`Create a snippet`}</button>
          </div>
        ) : (
          <div>
            <div
              className="flex align-center pl3 pr2"
              style={{ paddingTop: 10, paddingBottom: 8 }}
            >
              <div className="flex-full">
                <div
                  /* Hide the search input by collapsing dimensions rather than `display: none`.
                     This allows us to immediately focus on it when showSearch is set to true.*/
                  style={showSearch ? {} : { width: 0, height: 0 }}
                  className="overflow-hidden"
                >
                  <input
                    className="input input--borderless p0"
                    ref={e => (this.searchBox = e)}
                    onChange={e =>
                      this.setState({ searchString: e.target.value })
                    }
                    value={searchString}
                    onKeyDown={e => {
                      if (e.key === "Escape") {
                        this.hideSearch();
                      }
                    }}
                  />
                </div>
                <span className={cx({ hide: showSearch }, "text-heavy h3")}>
                  {snippetCollection.id !== "root" ? (
                    <span
                      className="text-brand-hover cursor-pointer"
                      onClick={() =>
                        this.props.setSnippetCollectionId(
                          snippetCollection.parent_id,
                        )
                      }
                    >
                      <Icon name="chevronleft" className="mr1" />
                      {snippetCollection.name}
                    </span>
                  ) : (
                    t`Snippets`
                  )}
                </span>
              </div>
              <div className="flex-align-right text-medium no-decoration">
                {items.length >= MIN_SNIPPETS_FOR_SEARCH && (
                  <Icon
                    className={cx(
                      { hide: showSearch },
                      "text-brand-hover cursor-pointer mr2",
                    )}
                    onClick={this.showSearch}
                    name="search"
                    size={HEADER_ICON_SIZE}
                  />
                )}

                <PopoverWithTrigger
                  triggerElement={
                    <Icon
                      className={cx(
                        { hide: showSearch },
                        "text-brand bg-light-hover rounded p1 cursor-pointer",
                      )}
                      name="add"
                      size={HEADER_ICON_SIZE}
                    />
                  }
                >
                  {({ onClose }) => (
                    <div className="flex flex-column">
                      {[
                        {
                          icon: "snippet",
                          name: t`New snippet`,
                          onClick: () =>
                            openSnippetModalWithSelectedText({ collectionId }),
                        },
                        {
                          icon: "folder",
                          name: t`New folder`,
                          onClick: () =>
                            this.setState({
                              modalSnippetCollection: {
                                parent_id: snippetCollection.id,
                              },
                            }),
                        },
                      ].map(({ icon, name, onClick }) => (
                        <div
                          className="p2 bg-medium-hover flex cursor-pointer text-brand-hover"
                          onClick={() => {
                            onClick();
                            onClose();
                          }}
                        >
                          <Icon name={icon} size={ICON_SIZE} className="mr1" />
                          <h4>{name}</h4>
                        </div>
                      ))}
                    </div>
                  )}
                </PopoverWithTrigger>
                <Icon
                  className={cx(
                    { hide: !showSearch },
                    "text-brand-hover cursor-pointer",
                  )}
                  onClick={this.hideSearch}
                  name="close"
                  size={HEADER_ICON_SIZE}
                />
              </div>
            </div>
            <div className="flex-full">
              {items.length > 0
                ? filteredSnippets.map(item =>
                    item.model === "collection" ? (
                      <CollectionRow
                        key={"collection-" + item.id}
                        collection={item}
                        onSelectCollection={() =>
                          this.props.setSnippetCollectionId(item.id)
                        }
                        onEdit={collection =>
                          this.setState({ modalSnippetCollection: collection })
                        }
                      />
                    ) : (
                      <SnippetRow
                        key={"snippet-" + item.id}
                        snippet={snippetsById[item.id]}
                        insertSnippet={this.props.insertSnippet}
                        setModalSnippet={this.props.setModalSnippet}
                      />
                    ),
                  )
                : null}
            </div>
          </div>
        )}
        {this.state.modalSnippetCollection && (
          <SnippetCollectionModal
            collection={this.state.modalSnippetCollection}
            onClose={() => this.setState({ modalSnippetCollection: null })}
            onSaved={() => {
              this.setState({ modalSnippetCollection: null });
              this.props.reload();
            }}
          />
        )}
      </SidebarContent>
    );
  }
}

@Snippets.loadList({ query: { archived: true }, wrapped: true })
class ArchivedSnippets extends React.Component {
  render() {
    const { onBack, snippets } = this.props;
    return (
      <SidebarContent>
        <SidebarHeader
          className="p2"
          title={t`Archived snippets`}
          onBack={onBack}
        />

        {snippets.map(snippet => (
          <SnippetRow
            key={snippet.id}
            snippet={snippet}
            unarchiveSnippet={() => snippet.update({ archived: false })}
          />
        ))}
      </SidebarContent>
    );
  }
}

class SnippetRow extends React.Component {
  state: { isOpen: boolean };

  constructor(props) {
    super(props);
    this.state = { isOpen: false };
  }

  render() {
    const {
      snippet,
      insertSnippet,
      setModalSnippet,
      unarchiveSnippet,
    } = this.props;
    const { description, content } = snippet;
    const { isOpen } = this.state;
    return (
      <div
        className={cx(
          { "border-transparent": !isOpen },
          "border-bottom border-top",
        )}
      >
        <div
          className="cursor-pointer bg-light-hover text-bold flex align-center justify-between p2 hover-parent hover--display"
          onClick={() => this.setState({ isOpen: !isOpen })}
        >
          <div
            className="flex text-brand-hover"
            onClick={
              unarchiveSnippet
                ? () => this.setState({ isOpen: true })
                : e => {
                    e.stopPropagation();
                    insertSnippet(snippet);
                  }
            }
          >
            <Icon
              name="snippet"
              size={ICON_SIZE}
              className="hover-child--hidden text-light"
            />
            <Icon
              name={insertSnippet ? "arrow_left_to_line" : "snippet"}
              size={ICON_SIZE}
              className="hover-child"
            />
            <span className="flex-full ml1">{snippet.name}</span>
          </div>
          <Icon
            name={isOpen ? "chevronup" : "chevrondown"}
            size={ICON_SIZE}
            className={cx({ "hover-child": !isOpen })}
          />
        </div>
        {isOpen && (
          <div className="px3 pb2 pt1">
            {description && <p className="text-medium mt0">{description}</p>}
            <pre
              className="bg-light bordered rounded p1 text-monospace text-small text-pre-wrap overflow-scroll overflow-x-scroll"
              style={{ maxHeight: 320 }}
            >
              {content}
            </pre>
            <Button
              onClick={
                unarchiveSnippet
                  ? unarchiveSnippet
                  : () => setModalSnippet(snippet)
              }
              borderless
              medium
              className="text-brand text-white-hover bg-light bg-brand-hover mt1"
              icon={unarchiveSnippet ? "unarchive" : "pencil"}
            >
              {unarchiveSnippet ? t`Unarchive` : t`Edit`}
            </Button>
          </div>
        )}
      </div>
    );
  }
}

class CollectionRow extends React.Component {
  render() {
    const { collection, onSelectCollection, onEdit } = this.props;
    return (
      <div
        className="hover-parent hover--visibility flex align-center p2 text-brand bg-light-hover cursor-pointer"
        onClick={onSelectCollection}
      >
        <Icon name="folder" size={ICON_SIZE} style={{ opacity: 0.25 }} />
        <span className="flex-full ml1 text-bold">{collection.name}</span>
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
                      {
                        name: t`Change permissions`,
                        onClick: () => alert("not implemented"),
                      },
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
      </div>
    );
  }
}

@SnippetCollections.load({ id: (state, props) => props.collection.id })
class SnippetCollectionModal extends React.Component {
  render() {
    const {
      snippetCollection,
      collection: passedCollection,
      onClose,
      onSaved,
    } = this.props;
    const collection = snippetCollection || passedCollection;
    return (
      <Modal onClose={onClose}>
        <SnippetCollections.ModalForm
          title={
            collection.id == null
              ? t`Create your new folder`
              : t`Editing ${collection.name}`
          }
          snippetCollection={collection}
          onClose={onClose}
          onSaved={onSaved}
        />
      </Modal>
    );
  }
}
