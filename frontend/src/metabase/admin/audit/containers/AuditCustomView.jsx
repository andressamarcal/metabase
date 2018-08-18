/* @flow weak */

import React from "react";

import "./AuditTableVisualization";

import QuestionResultLoader from "metabase/containers/QuestionResultLoader";

import Question from "metabase-lib/lib/Question";
import { connect } from "react-redux";
import { push } from "react-router-redux";
import { getMetadata } from "metabase/selectors/metadata";

const mapStateToProps = (state, props) => ({
  metadata: getMetadata(state),
});

@connect(mapStateToProps, null)
export default class AuditTable extends React.Component {
  render() {
    const { metadata, card, ...props } = this.props;
    const question = new Question(metadata, card.card);

    return (
      <QuestionResultLoader className="mt3" question={question}>
        {this.props.children}
      </QuestionResultLoader>
    );
  }
}
