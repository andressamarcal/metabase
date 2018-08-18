import React from "react";

import AuditContent from "../components/AuditContent";
import AuditCustomView from "../containers/AuditCustomView";

import OpenInMetabase from "../components/OpenInMetabase";
import EntityName from "metabase/entities/containers/EntityName";

import * as QueryDetailCards from "../lib/cards/query_detail";

import { serializeCardForUrl } from "metabase/lib/card";

const AuditQueryDetail = ({ params: { queryHash } }) => (
  <AuditCustomView card={QueryDetailCards.details(queryHash)}>
    {({ result }) => {
      if (!result) {
        return null;
      }
      const datasetQuery = result.data.rows[0][0];
      console.log("datasetQuery", datasetQuery);
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
          <div>
            <h4>Database:</h4>
            <EntityName
              entityType="databases"
              entityId={datasetQuery.database}
              property={"name"}
            />
            <h4>Query:</h4>
            {datasetQuery.type === "native" ? (
              <pre className="text-code p1">{datasetQuery.native.query}</pre>
            ) : null}
          </div>
        </AuditContent>
      );
    }}
  </AuditCustomView>
);

export default AuditQueryDetail;
