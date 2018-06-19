import React, { Component } from "react";
import { t } from "c-3po";

import SettingsBatchForm from "./SettingsBatchForm";

export default class SettingsSAMLForm extends Component {
  render() {
    return (
      <SettingsBatchForm
        {...this.props}
        breadcrumbs={[
          [t`Authentication`, "/admin/settings/authentication"],
          [t`SAML`],
        ]}
        enabledKey="saml-enabled"
        layout={[
          {
            title: t`Server Settings`,
            settings: [
              "saml-enabled",
              "saml-identity-provider-uri",
              "saml-identity-provider-certificate",
              "saml-application-name",
            ],
          },
          {
            title: t`Sign SSO requests (optional)`,
            collapse: true,
            settings: [
              "saml-keystore-path",
              "saml-keystore-password",
              "saml-keystore-alias",
            ],
          },
          {
            title: t`User attribute configuration (optional)`,
            collapse: true,
            settings: [
              "saml-attribute-email",
              "saml-attribute-firstname",
              "saml-attribute-lastname",
            ],
          },
        ]}
      />
    );
  }
}
