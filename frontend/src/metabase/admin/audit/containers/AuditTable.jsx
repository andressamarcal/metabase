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
  question: new Question(getMetadata(state), {
    ...props.table.card,
    display: "audit-table",
  }),
});

const mapDispatchToProps = {
  onChangeLocation: push,
};

const AuditTable = connect(mapStateToProps, mapDispatchToProps)(
  ({ question, onChangeLocation, ...props }) => {
    return (
      <QuestionLoadAndDisplay
        className="mt2"
        question={question}
        actionsForClick={auditActionsForClick}
        onChangeLocation={onChangeLocation}
        onChangeCardAndRun={() => {}}
      />
    );
  },
);

export default AuditTable;
