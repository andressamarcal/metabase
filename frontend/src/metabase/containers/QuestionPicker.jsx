import React from "react";

import CardPicker from "metabase/pulse/components/CardPicker";
import QuestionListLoader from "metabase/containers/QuestionListLoader";

export default class QuestionPicker extends React.Component {
  render() {
    return (
      <QuestionListLoader>
        {({ questions }) => <CardPicker {...this.props} cardList={questions} />}
      </QuestionListLoader>
    );
  }
}
