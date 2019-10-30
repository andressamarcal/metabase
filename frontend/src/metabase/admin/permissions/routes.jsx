import React from "react";
import { Route } from "metabase/hoc/Title";
import { IndexRedirect, IndexRoute } from "react-router";
import { t } from "ttag";
import DataPermissionsApp from "./containers/DataPermissionsApp";
import DatabasesPermissionsApp from "./containers/DatabasesPermissionsApp";
import SchemasPermissionsApp from "./containers/SchemasPermissionsApp";
import TablesPermissionsApp from "./containers/TablesPermissionsApp";
import CollectionPermissions from "./containers/CollectionsPermissionsApp";

import { ModalRoute } from "metabase/hoc/ModalRoute";
import GTAPModal from "metabase/plugins/mt/components/GTAPModal";

const getRoutes = store => (
  <Route title={t`Permissions`} path="permissions">
    <IndexRedirect to="databases" />

    {/* "DATABASES" a.k.a. "data" section */}
    <Route path="databases" component={DataPermissionsApp}>
      {/* DATABASES */}
      <IndexRoute component={DatabasesPermissionsApp} />

      {/* SCHEMAS */}
      <Route path=":databaseId/schemas" component={SchemasPermissionsApp} />

      {/* TABLES */}
      <Route
        path=":databaseId/schemas/:schemaName/tables"
        component={TablesPermissionsApp}
      >
        <ModalRoute
          path=":tableId/segmented/group/:groupId"
          modal={GTAPModal}
        />
      </Route>

      {/* TABLES NO SCHEMA */}
      {/* NOTE: this route is to support null schemas, inject the empty string as the schemaName */}
      <Route
        path=":databaseId/tables"
        component={(
          props, // eslint-disable-line react/display-name
        ) => (
          <TablesPermissionsApp
            {...props}
            params={{ ...props.params, schemaName: "" }}
          />
        )}
      >
        <ModalRoute
          path=":tableId/segmented/group/:groupId"
          modal={GTAPModal}
        />
      </Route>
    </Route>

    {/* "COLLECTIONS" section */}
    <Route path="collections" component={CollectionPermissions}>
      <Route path=":collectionId" />
    </Route>
  </Route>
);

export default getRoutes;
