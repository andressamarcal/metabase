/* @flow */

import React from "react";

import PopoverWithTrigger from "metabase/components/PopoverWithTrigger.jsx";
import ParameterTargetList from "../components/ParameterTargetList";

import _ from "underscore";
import cx from "classnames";

import type {
  ParameterMappingUIOption,
  ParameterTarget,
} from "metabase/meta/types/Parameter";

type Props = {
  target: ?ParameterTarget,
  onChange: (target: ?ParameterTarget) => void,
  mappingOptions: ParameterMappingUIOption[],
  children?: React$Element<any> | (any => React$Element<any>),
};

export default class ParameterTargetWidget extends React.Component {
  props: Props;

  render() {
    const { target, onChange, mappingOptions, children } = this.props;

    const disabled = mappingOptions.length === 0;
    const selected = _.find(mappingOptions, o => _.isEqual(o.target, target));
    const mappingOptionSections = _.groupBy(mappingOptions, "sectionName");

    const hasFkOption = _.any(mappingOptions, o => !!o.isFk);

    const sections = _.map(mappingOptionSections, options => ({
      name: options[0].sectionName,
      items: options,
    }));

    return (
      <PopoverWithTrigger
        ref="popover"
        triggerClasses={cx({ disabled: disabled })}
        sizeToFit
        triggerElement={
          typeof children === "function"
            ? children({ selected, disabled })
            : children
        }
      >
        <ParameterTargetList
          onChange={target => {
            onChange(target);
            this.refs.popover.close();
          }}
          target={target}
          mappingOptions={mappingOptions}
        />
      </PopoverWithTrigger>
    );
  }
}
