import React from "react";

import { connect } from "react-redux";
import { loadQuestions } from "metabase/questions/questions";
import { getAllQuestions } from "metabase/questions/selectors";

const mapStateToProps = state => ({
  questions: getAllQuestions(state),
});

const mapDispatchToProps = {
  loadQuestions,
};

@connect(mapStateToProps, mapDispatchToProps)
export default class QuestionsLoader extends React.Component {
  componentWillMount() {
    this.props.loadQuestions();
  }
  render() {
    return this.props.children(this.props.questions);
  }
}
