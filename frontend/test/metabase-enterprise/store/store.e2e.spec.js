import { createTestStore, useSharedAdminLogin } from "__support__/e2e";
import { mount } from "enzyme";

import { delay } from "metabase/lib/promise";
import "metabase/plugins/builtin";
import "metabase-enterprise/plugins";

describe("admin/store", () => {
  beforeAll(async () => {
    useSharedAdminLogin();
  });

  it("should show the nav item and tab contents", async () => {
    const store = await createTestStore();
    store.pushPath("/admin/store");
    const app = mount(store.getAppContainer());

    await delay(100); // 😢

    // shows "Enterprise" in the top nav
    expect(app.find(".NavItem").map(n => n.text())).toContain("Enterprise");

    // displays the four feature boxes
    expect(app.find("h3").map(n => n.text())).toEqual([
      "Data sandboxes",
      "White labeling",
      "Auditing",
      "Single sign-on",
    ]);
  });
});
