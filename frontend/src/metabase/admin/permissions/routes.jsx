import React from "react";
import { Route } from "metabase/hoc/Title";
import { IndexRedirect } from "react-router";
import { t } from "c-3po";
import DataPermissionsApp from "./containers/DataPermissionsApp.jsx";
import DatabasesPermissionsApp from "./containers/DatabasesPermissionsApp.jsx";
import SchemasPermissionsApp from "./containers/SchemasPermissionsApp.jsx";
import TablesPermissionsApp from "./containers/TablesPermissionsApp.jsx";

import { ModalRoute } from "metabase/hoc/ModalRoute";
import GTAPModal from "metabase/plugins/mt/components/GTAPModal";

const getRoutes = store => (
  <Route
    title={t`Permissions`}
    path="permissions"
    component={DataPermissionsApp}
  >
    <IndexRedirect to="databases" />
    <Route path="databases" component={DatabasesPermissionsApp} />
    <Route
      path="databases/:databaseId/schemas"
      component={SchemasPermissionsApp}
    />
    <Route
      path="databases/:databaseId/schemas/:schemaName/tables"
      component={TablesPermissionsApp}
    >
      <ModalRoute path=":tableId/segmented/group/:groupId" modal={GTAPModal} />
    </Route>

    {/* NOTE: this route is to support null schemas, inject the empty string as the schemaName */}
    <Route
      path="databases/:databaseId/tables"
      component={(
        props, // eslint-disable-line react/display-name
      ) => (
        <TablesPermissionsApp
          {...props}
          params={{ ...props.params, schemaName: "" }}
        />
      )}
    >
      <ModalRoute path=":tableId/segmented/group/:groupId" modal={GTAPModal} />
    </Route>
  </Route>
);

export default getRoutes;
