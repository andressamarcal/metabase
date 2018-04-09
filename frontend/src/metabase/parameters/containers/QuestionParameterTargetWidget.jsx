/* @flow */

import React from "react";

import ParameterTargetWidget from "../components/ParameterTargetWidget";
import { QuestionLoaderHOC } from "metabase/containers/QuestionLoader";
import SelectButton from "metabase/components/SelectButton";

import * as Dashboard from "metabase/meta/Dashboard";

import type { Parameter, ParameterTarget } from "metabase/meta/types/Parameter";

type Props = {
  questionId: ?number,
  parameter?: Parameter,
  target: ?ParameterTarget,
  onChange: (target: ?ParameterTarget) => void,
};

@QuestionLoaderHOC
export default class QuestionParameterTargetWidget extends React.Component {
  props: Props;

  static defaultProps = {
    children: ({ selected }) => (
      <SelectButton hasValue={!!selected} className="border-med">
        {selected ? selected.name : "Select a target"}
      </SelectButton>
    ),
  };

  render() {
    // $FlowFixMe: question provided by HOC
    const { question, parameter, ...props } = this.props;
    const mappingOptions = question
      ? Dashboard.getParameterMappingOptions(
          question.metadata(),
          parameter,
          question.card(),
        )
      : [];
    return <ParameterTargetWidget {...props} mappingOptions={mappingOptions} />;
  }
}
