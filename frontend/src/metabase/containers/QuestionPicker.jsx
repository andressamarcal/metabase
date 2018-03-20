import React from "react";

import CardPicker from "metabase/pulse/components/CardPicker";
import QuestionsLoader from "metabase/containers/QuestionsLoader";

import _ from "underscore";

export default class QuestionPicker extends React.Component {
  render() {
    return (
      <QuestionsLoader>
        {questions => <CardPicker {...this.props} cardList={questions} />}
      </QuestionsLoader>
    );
  }
}
