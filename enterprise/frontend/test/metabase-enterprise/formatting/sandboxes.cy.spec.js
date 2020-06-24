import {
  restore,
  signInAsAdmin,
  openOrdersTable,
  signInAsNormalUser,
  signOut,
} from "../../../../../frontend/test/__support__/cypress";

import { changePermissionsforSandbox } from "../_support_/cypress";

const new_user = {
  first_name: "Barb",
  last_name: "Tabley",
  username: "new@metabase.com",
};

let questionId;

describe("formatting > sandboxes", () => {
  before(restore);

  describe("Setup for sandbox tests", () => {
    beforeEach(signInAsAdmin);

    it("should make SQL question", () => {
      cy.request("POST", "/api/card", {
        name: "sql param",
        dataset_query: {
          type: "native",
          native: {
            query:
              "select * from products join orders on products.id = orders.product_id where orders.created_at > dateadd(year,-5,getdate())",
          },
          database: 1,
        },
        display: "scalar",
        visualization_settings: {},
      }).then(({ body }) => {
        questionId = body.id;
      });

      cy.visit("/collection/root");
      cy.wait(2000)
        .findByText("sql param")
        .click();
      cy.findByText("Save").click();
      cy.findAllByText("Save")
        .last()
        .click();
    });

    it("should make a JOINs table", () => {
      openOrdersTable();
      cy.wait(1000)
        .get(".Icon-notebook")
        .click();
      cy.wait(1000)
        .findByText("Join data")
        .click();
      cy.findByText("Products").click();
      cy.findByText("Visualize").click();
      cy.findByText("Save").click();

      cy.findByLabelText("Name")
        .clear()
        .wait(1)
        .type("test joins table");
      cy.findAllByText("Save")
        .last()
        .click();
      cy.findByText("Not now").click();
    });
  });

  describe("Sandboxes should work", () => {
    beforeEach(signInAsNormalUser);

    it("should add key attributes to new user and existing user", () => {
      signOut();
      signInAsAdmin();

      // Existing user
      cy.visit("/admin/people");
      cy.get(".Icon-ellipsis")
        .last()
        .click();
      cy.findByText("Edit user").click();
      cy.findByText("Add an attribute").click();
      cy.findByPlaceholderText("Key").type("User ID");
      cy.findByPlaceholderText("Value").type("3");
      cy.findByText("Update").click();

      // New user
      cy.visit("/admin/people");
      cy.findByText("Add someone").click();
      cy.findByPlaceholderText("Johnny").type(new_user.first_name);
      cy.findByPlaceholderText("Appleseed").type(new_user.last_name);
      cy.findByPlaceholderText("youlooknicetoday@email.com").type(
        new_user.username,
      );
      cy.findByText("Add an attribute").click();
      cy.findByPlaceholderText("Key").type("User ID");
      cy.findByPlaceholderText("Value").type("1");
      cy.findAllByText("Create").click();
      cy.findByText("Done").click();
    });

    it("should change sandbox permissions as admin", () => {
      signOut();
      signInAsAdmin();

      cy.visit("/admin/permissions/databases/1/schemas/PUBLIC/tables");
      changePermissionsforSandbox(13, "User ID", "User ID", "first");
      changePermissionsforSandbox(18, "ID", "User ID", "second");
      cy.findByText("Save Changes").click();
      cy.findByText("Yes").click();
      cy.findByText("Save Changes").should("not.exist");
    });

    it("should filter on normal tables", () => {
      cy.visit("/browse/1");
      cy.findByText("Orders").click();

      // Table filter
      cy.wait(3000)
        .get(".TableInteractive-cellWrapper--lastColumn")
        .contains("1")
        .should("not.exist");
      cy.get(".TableInteractive-cellWrapper--lastColumn")
        .last()
        .contains("3");

      // Notebook filter
      cy.get(".Icon-notebook").click();
      cy.wait(2000)
        .findByText("Summarize")
        .click();
      cy.findByText("Count of rows").click();
      cy.findByText("Visualize").click();
      cy.findByText("18,760").should("not.exist");
      cy.findByText("10");
    });

    it("should filter on saved SQL question", () => {
      // *** Test fails (Issue #403)
      cy.visit("/question/4");
      cy.get(".TableInteractive-cellWrapper--firstColumn").should(
        "have.length",
        11,
      );
    });

    it("should filter on a JOINed table", () => {
      cy.visit("/question/5");

      // Table filter
      cy.wait(2000)
        .get(".TableInteractive-cellWrapper--firstColumn")
        .should("have.length", 11);

      // Notebook filter
      cy.get(".Icon-notebook").click();
      cy.wait(2000)
        .findByText("Filter")
        .click();
      cy.findAllByText("Total")
        .last()
        .click();
      cy.findByText("Equal to").click();
      cy.findByText("Greater than").click();
      cy.findByPlaceholderText("Enter a number").type("100");
      cy.findByText("Add filter").click();
      cy.findByText("Visualize").click();
      cy.wait(2000)
        .get(".TableInteractive-cellWrapper--firstColumn")
        .should("have.length", 7);
    });
  });
});
