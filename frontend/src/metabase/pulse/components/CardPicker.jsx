/* eslint "react/prop-types": "warn" */
import React, { Component } from "react";
import PropTypes from "prop-types";
import ReactDOM from "react-dom";
import { t } from "c-3po";

import Icon from "metabase/components/Icon.jsx";
import Popover from "metabase/components/Popover.jsx";

import _ from "underscore";

export default class CardPicker extends Component {
  state = {
    isOpen: false,
    inputValue: "",
    inputWidth: 300,
    collectionId: undefined,
  };

  static propTypes = {
    value: PropTypes.number,
    onChange: PropTypes.func.isRequired,
    cardList: PropTypes.array.isRequired,
    attachmentsEnabled: PropTypes.bool,
    autoFocus: PropTypes.bool,
    // function that takes a card object and  returns an error string if the
    // card can't be selected
    checkCard: PropTypes.func,
  };

  static defaultProps = {
    checkCard: () => null,
  };

  componentWillUnmount() {
    clearTimeout(this._timer);
  }

  onInputChange = ({ target }) => {
    this.setState({ inputValue: target.value });
  };

  onInputFocus = () => {
    this.setState({ isOpen: true });
  };

  onInputBlur = () => {
    // Without a timeout here isOpen gets set to false when an item is clicked
    // which causes the click handler to not fire. For some reason this even
    // happens with a 100ms delay, but not 200ms?
    clearTimeout(this._timer);
    this._timer = setTimeout(() => {
      if (!this.state.isClicking) {
        this.setState({ isOpen: false });
      } else {
        this.setState({ isClicking: false });
      }
    }, 250);
  };

  onChange = id => {
    this.props.onChange(id);
    ReactDOM.findDOMNode(this.refs.input).blur();
    this.setState({ isOpen: false });
  };

  renderItem(card) {
    const error = this.props.checkCard(card);
    if (error) {
      return (
        <li key={card.id} className="px2 py1">
          <h4 className="text-grey-2">{card.name}</h4>
          <h4 className="text-gold mt1">{error}</h4>
        </li>
      );
    } else {
      return (
        <li
          key={card.id}
          className="List-item cursor-pointer"
          onClickCapture={this.onChange.bind(this, card.id)}
        >
          <h4 className="List-item-title px2 py1">{card.name}</h4>
        </li>
      );
    }
  }

  // keep the modal width in sync with the input width :-/
  componentDidUpdate() {
    let { scrollWidth } = ReactDOM.findDOMNode(this.refs.input);
    if (this.state.inputWidth !== scrollWidth) {
      this.setState({ inputWidth: scrollWidth });
    }
  }

  render() {
    let { value, cardList, autoFocus } = this.props;

    let { isOpen, inputValue, inputWidth, collectionId } = this.state;

    let cardByCollectionId = _.groupBy(cardList, "collection_id");
    let collectionIds = Object.keys(cardByCollectionId);

    let selectedCard;
    const collectionById = {};
    for (const card of cardList) {
      if (card.collection) {
        collectionById[card.collection.id] = card.collection;
      }
      if (value != null && card.id === value) {
        selectedCard = card;
      }
    }
    const collections = collectionIds
      .map(id => collectionById[id])
      .filter(c => c)
      // add "Everything else" as the last option for cards without a collection
      .concat([{ id: null, name: t`Everything else` }]);

    let visibleCardList;
    if (inputValue) {
      let searchString = inputValue.toLowerCase();
      visibleCardList = cardList.filter(
        card =>
          ~(card.name || "").toLowerCase().indexOf(searchString) ||
          ~(card.description || "").toLowerCase().indexOf(searchString),
      );
    } else {
      if (collectionId !== undefined) {
        visibleCardList = cardByCollectionId[collectionId];
      } else if (collectionIds.length === 1) {
        visibleCardList = cardByCollectionId[collectionIds[0]];
      }
    }

    const collection = _.findWhere(collections, { id: collectionId });
    return (
      <div className="CardPicker flex-full">
        <input
          ref="input"
          className="input no-focus full text-bold"
          placeholder={t`Type a question name to filter`}
          value={!isOpen && selectedCard ? selectedCard.name : inputValue}
          onFocus={this.onInputFocus}
          onBlur={this.onInputBlur}
          onChange={this.onInputChange}
          autoFocus={autoFocus}
        />
        <Popover
          isOpen={isOpen && cardList.length > 0}
          hasArrow={false}
          tetherOptions={{
            attachment: "top left",
            targetAttachment: "bottom left",
            targetOffset: "0 0",
          }}
        >
          <div
            className="rounded bordered scroll-y scroll-show"
            style={{ width: inputWidth + "px", maxHeight: "400px" }}
          >
            {visibleCardList &&
              collectionIds.length > 1 && (
                <div
                  className="flex align-center text-slate cursor-pointer border-bottom p2"
                  onClick={e => {
                    this.setState({
                      collectionId: undefined,
                      isClicking: true,
                    });
                  }}
                >
                  <Icon name="chevronleft" size={18} />
                  <h3 className="ml1">{collection && collection.name}</h3>
                </div>
              )}
            {visibleCardList ? (
              <ul className="List text-brand">
                {visibleCardList.map(card => this.renderItem(card))}
              </ul>
            ) : collections ? (
              <CollectionList>
                {collections.map(collection => (
                  <CollectionListItem
                    key={collection.id}
                    collection={collection}
                    onClick={e => {
                      this.setState({
                        collectionId: collection.id,
                        isClicking: true,
                      });
                    }}
                  />
                ))}
              </CollectionList>
            ) : null}
          </div>
        </Popover>
      </div>
    );
  }
}

const CollectionListItem = ({ collection, onClick }) => (
  <li
    className="List-item cursor-pointer flex align-center py1 px2"
    onClick={onClick}
  >
    <Icon
      name="collection"
      style={{ color: collection.color }}
      className="Icon mr2 text-default"
      size={18}
    />
    <h4 className="List-item-title">{collection.name}</h4>
    <Icon name="chevronright" className="flex-align-right text-grey-2" />
  </li>
);

CollectionListItem.propTypes = {
  collection: PropTypes.object.isRequired,
  onClick: PropTypes.func.isRequired,
};

const CollectionList = ({ children }) => (
  <ul className="List text-brand">{children}</ul>
);

CollectionList.propTypes = {
  children: PropTypes.array.isRequired,
};
