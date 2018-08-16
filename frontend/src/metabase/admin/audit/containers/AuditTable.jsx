/* @flow weak */

import React from "react";

import "./AuditTableVisualization";

import QuestionLoadAndDisplay from "metabase/containers/QuestionLoadAndDisplay";

import Question from "metabase-lib/lib/Question";
import { connect } from "react-redux";
import { push } from "react-router-redux";
import { getMetadata } from "metabase/selectors/metadata";

import { auditActionsForClick } from "../lib/util";

const mapStateToProps = (state, props) => ({
  metadata: getMetadata(state),
});

const mapDispatchToProps = {
  onChangeLocation: push,
};

@connect(mapStateToProps, mapDispatchToProps)
export default class AuditTable extends React.Component {
  render() {
    const { metadata, table, onChangeLocation, ...props } = this.props;
    const question = new Question(metadata, {
      ...table.card,
      display: "audit-table",
    });

    return (
      <QuestionLoadAndDisplay
        className="mt3"
        question={question}
        actionsForClick={auditActionsForClick}
        onChangeLocation={onChangeLocation}
        onChangeCardAndRun={() => {}}
      />
    );
  }
}
