import React from "react";
import EntityObjectLoader from "./EntityObjectLoader";

const EntityName = ({ entityId, entityType }) => (
  <EntityObjectLoader
    entityType={entityType}
    entityId={entityId}
    properties={["name"]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => (object ? <span>{object.name}</span> : null)}
  </EntityObjectLoader>
);

export default EntityName;
