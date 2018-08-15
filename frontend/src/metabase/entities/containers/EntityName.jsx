import React from "react";
import EntityObjectLoader from "./EntityObjectLoader";

const EntityName = ({ entityId, entityType, property = "name" }) => (
  <EntityObjectLoader
    entityType={entityType}
    entityId={entityId}
    properties={[property]}
    loadingAndErrorWrapper={false}
  >
    {({ object }) => (object ? <span>{object[property]}</span> : null)}
  </EntityObjectLoader>
);

export default EntityName;
