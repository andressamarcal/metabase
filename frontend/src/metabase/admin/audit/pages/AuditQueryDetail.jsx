/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditCustomView from "../containers/AuditCustomView";

import OpenInMetabase from "../components/OpenInMetabase";

import NativeQueryEditor from "metabase/query_builder/components/NativeQueryEditor";
import GuiQueryEditor from "metabase/query_builder/components/GuiQueryEditor";
import Question from "metabase-lib/lib/Question";

import * as QueryDetailCards from "../lib/cards/query_detail";

import { serializeCardForUrl } from "metabase/lib/card";

type Props = {
  params: { [key: string]: string },
};

const AuditQueryDetail = ({ params: { queryHash } }: Props) => (
  <AuditCustomView card={QueryDetailCards.details(queryHash)}>
    {({ result }) => {
      if (!result) {
        return null;
      }
      const datasetQuery = result.data.rows[0][0];
      if (!datasetQuery) {
        return <div>Query Not Recorded, sorry</div>;
      }

      return (
        <AuditContent
          title="Query"
          subtitle={
            <OpenInMetabase
              to={
                "/question#" +
                serializeCardForUrl({
                  dataset_query: datasetQuery,
                })
              }
            />
          }
        >
          <div className="pt4" style={{ pointerEvents: "none" }}>
            <QueryBuilderReadOnly
              card={{
                name: "",
                visualization_settings: {},
                display: "table",
                dataset_query: datasetQuery,
              }}
            />
          </div>
        </AuditContent>
      );
    }}
  </AuditCustomView>
);

import { connect } from "react-redux";
import { getMetadata } from "metabase/selectors/metadata";

import NativeQuery from "metabase-lib/lib/queries/NativeQuery";

import { fetchDatabases } from "metabase/redux/metadata";
import { loadMetadataForCard } from "metabase/query_builder/actions";

const mapStateToProps = state => ({
  metadata: getMetadata(state),
});
const mapDispatchToProps = {
  loadMetadataForCard,
  fetchDatabases,
};

@connect(
  mapStateToProps,
  mapDispatchToProps,
)
class QueryBuilderReadOnly extends React.Component {
  componentDidMount() {
    const { card, fetchDatabases, loadMetadataForCard } = this.props;
    fetchDatabases();
    loadMetadataForCard(card);
  }
  render() {
    const { card, metadata } = this.props;
    const question = new Question(metadata, card);

    const query = question.query();

    if (query instanceof NativeQuery) {
      return (
        <NativeQueryEditor
          question={question}
          query={query}
          location={{ query: {} }}
          readOnly
        />
      );
    } else {
      const tableMetadata = query.table();
      return tableMetadata ? (
        <GuiQueryEditor
          datasetQuery={card.dataset_query}
          query={query}
          databases={tableMetadata && [tableMetadata.db]}
          readOnly
        />
      ) : null;
    }
  }
}

export default AuditQueryDetail;
