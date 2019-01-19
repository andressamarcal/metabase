import React, { Component } from "react";
import PropTypes from "prop-types";

import Link from "metabase/components/Link";

import colors from "metabase/lib/colors";

import cx from "classnames";
import _ from "underscore";

export default class Radio extends Component {
  static propTypes = {
    value: PropTypes.any,
    options: PropTypes.array.isRequired,
    onChange: PropTypes.func,
    optionNameFn: PropTypes.func,
    optionValueFn: PropTypes.func,
    optionKeyFn: PropTypes.func,
    vertical: PropTypes.bool,
    underlined: PropTypes.bool,
    showButtons: PropTypes.bool,
    // use <Link> tags instead of `onChange`
    links: PropTypes.bool,
    py: PropTypes.number,
  };

  static defaultProps = {
    optionNameFn: option => option.name,
    optionValueFn: option => option.value,
    optionKeyFn: option => option.value,
    vertical: false,
    underlined: false,
    links: false,
  };

  constructor(props, context) {
    super(props, context);
    this._id = _.uniqueId("radio-");
  }

  render() {
    const {
      value,
      options,
      onChange,
      optionNameFn,
      optionValueFn,
      optionKeyFn,
      vertical,
      underlined,
      className,
      py,
    } = this.props;
    // show buttons for vertical only by default
    const showButtons =
      this.props.showButtons != undefined ? this.props.showButtons : vertical;
    return (
      <ul
        className={cx(className, "flex", {
          "flex-column": vertical,
          "text-bold h3": !showButtons,
        })}
      >
        {options.map(option => {
          const selected = value === optionValueFn(option);

          const itemClassName = cx(
            "flex align-center cursor-pointer mr3",
            { "text-brand-hover": !showButtons },
            py != undefined ? `py${py}` : underlined ? "py2" : "pt1",
          );
          const itemActiveClassName = "text-brand";
          const itemStyle = {
            borderBottom: underlined ? `3px solid transparent` : undefined,
            borderColor: "transparent",
          };
          const itemActiveStyle = {
            borderColor: underlined ? colors["brand"] : "transparent",
          };

          const itemContent = (
            <span>
              <input
                className="Form-radio"
                type="radio"
                name={this._id}
                value={optionValueFn(option)}
                checked={selected}
                id={this._id + "-" + optionKeyFn(option)}
              />
              {showButtons && (
                <label htmlFor={this._id + "-" + optionKeyFn(option)} />
              )}
              {optionNameFn(option)}
            </span>
          );

          if (this.props.links) {
            return (
              <li key={optionKeyFn(option)}>
                <Link
                  to={optionValueFn(option)}
                  className={itemClassName}
                  activeClassName={itemActiveClassName}
                  style={itemStyle}
                  activeStyle={itemActiveStyle}
                >
                  {itemContent}
                </Link>
              </li>
            );
          } else {
            return (
              <li
                key={optionKeyFn(option)}
                className={cx(itemClassName, {
                  [itemActiveClassName]: selected,
                })}
                style={{ ...itemStyle, ...(selected ? itemActiveStyle : {}) }}
                onClick={e => onChange(optionValueFn(option))}
              >
                {itemContent}
              </li>
            );
          }
        })}
      </ul>
    );
  }
}
