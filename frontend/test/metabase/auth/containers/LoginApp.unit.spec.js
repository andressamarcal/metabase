import React from "react";

import "metabase/plugins/builtin";

import LoginApp from "metabase/auth/containers/LoginApp";

import { mountWithStore } from "__support__/integration";

jest.mock("metabase/components/LogoIcon", () => () => null);

jest.mock("metabase/lib/settings", () => ({
  get: () => ({
    tag: 1,
    version: 1,
  }),
  googleAuthEnabled: jest.fn(),
  ldapEnabled: jest.fn(),
}));

import Settings from "metabase/lib/settings";
const SELECTOR_FOR_EMAIL_LINK = `[to="/auth/login/password"]`;

describe("LoginApp", () => {
  describe("initial state", () => {
    describe("without SSO", () => {
      it("should show the login form", () => {
        const { wrapper } = mountWithStore(<LoginApp params={{}} />);
        expect(wrapper.find("FormField").length).toBe(2);
      });
    });
    describe("with SSO", () => {
      beforeEach(() => {
        Settings.googleAuthEnabled.mockReturnValue(true);
      });
      it("should show the SSO button", () => {
        const { wrapper } = mountWithStore(<LoginApp params={{}} />);
        expect(wrapper.find("AuthProviderButton").length).toBe(1);
        expect(wrapper.find(SELECTOR_FOR_EMAIL_LINK).length).toBe(1);
      });

      it("should hide the login form initially", () => {
        const { wrapper } = mountWithStore(<LoginApp params={{}} />);
        expect(wrapper.find("FormField").length).toBe(0);
      });

      it("should show the login form if the url param is set", () => {
        const { wrapper } = mountWithStore(
          <LoginApp params={{ provider: "password" }} />,
        );

        expect(wrapper.find("FormField").length).toBe(2);
        expect(wrapper.find("AuthProviderButton").length).toBe(0);
      });
    });
  });
});
