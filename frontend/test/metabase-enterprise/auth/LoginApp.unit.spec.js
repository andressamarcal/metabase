import React from "react";

import "__support__/mocks";
import "metabase/plugins/builtin";
import "metabase-enterprise/plugins";
import LoginApp from "metabase/auth/containers/LoginApp";

import { mountWithStore } from "__support__/integration";

const SELECTOR_FOR_EMAIL_LINK = `[to="/auth/login/password"]`;

jest.mock("metabase/components/LogoIcon", () => () => null);

jest.mock("metabase/lib/settings", () => ({
  get: key => ({ enable_password_login: false }[key]),
  googleAuthEnabled: jest.fn(),
  ldapEnabled: jest.fn(),

  // not actually used, required to load plugins:
  hasPremiumFeature: jest.fn(),
  docsUrl: jest.fn(),
}));

import Settings from "metabase/lib/settings";

describe("LoginApp - Enterprise", () => {
  describe("initial state", () => {
    describe("with Google and password disabled", () => {
      beforeEach(() => {
        Settings.googleAuthEnabled.mockReturnValue(true);
      });
      it("should show the SSO button without an option to use password", () => {
        const { wrapper } = mountWithStore(<LoginApp params={{}} />);
        expect(wrapper.find("AuthProviderButton").length).toBe(1);
        expect(wrapper.find(SELECTOR_FOR_EMAIL_LINK).length).toBe(0);
      });
    });
  });
});
