import {
  restore,
  signInAsAdmin,
  openOrdersTable,
  signInAsNormalUser,
  signOut,
  signIn,
  withSampleDataset,
} from "../../../../../frontend/test/__support__/cypress";

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
            query: "select id,name,address,email from people where {{cid}}",
            "template-tags": {
              cid: {
                id: "6b8b10ef-0104-1047-1e1b-2492d5954555",
                name: "cid",
                "display-name": "CID",
                type: "dimension",
                dimension: ["field-id", 21],
                "widget-type": "id",
              },
            },
          },
          database: 1,
        },
        display: "table",
        visualization_settings: {},
      }).then(({ body }) => {
        questionId = body.id;
      });
    });

    it("should make a JOINs table", () => {
      openOrdersTable();
      cy.wait(1000).get(".Icon-notebook").click();
      cy.wait(1000).findByText("Join data").click();
      cy.findByText("Products").click();
      cy.findByText("Visualize").click();
      cy.findByText("Save").click();

      cy.findByLabelText("Name").clear().wait(1).type("test joins table");
      cy.findAllByText("Save").last().click();
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
      cy.get(".Icon-ellipsis").last().click();
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
      const ADMIN_GROUP = 2;
      const DATA_GROUP = 5;

      // Changes Orders permssions to use filter and People to use SQL filter
      withSampleDataset(({ ORDERS_ID, PEOPLE_ID, PRODUCTS_ID, REVIEWS_ID }) => {
        cy.request("POST", "/api/mt/gtap", {
          id: 1,
          group_id: DATA_GROUP,
          table_id: ORDERS_ID,
          card_id: null,
          attribute_remappings: {
            "User ID": ["dimension", ["field-id", 9]],
          },
        });
        cy.request("POST", "/api/mt/gtap", {
          group_id: DATA_GROUP,
          table_id: PEOPLE_ID,
          card_id: 4,
          attribute_remappings: {
            "User ID": ["dimension", ["template-tag", "cid"]],
          },
        });
        cy.request("PUT", "/api/permissions/graph", {
          revision: 1,
          groups: {
            [ADMIN_GROUP]: { "1": { native: "write", schemas: "all" } },
            [DATA_GROUP]: {
              "1": {
                schemas: {
                  PUBLIC: {
                    [ORDERS_ID]: { query: "segmented", read: "all" },
                    [PEOPLE_ID]: { query: "segmented", read: "all" },
                    [PRODUCTS_ID]: "all",
                    [REVIEWS_ID]: "all",
                  },
                },
              },
            },
          },
        });
      });
    });

    it("should be sandboxed with a filter (on normal table)", () => {
      cy.visit("/browse/1");
      cy.findByText("Orders").click();

      // Table filter
      cy.wait(3000)
        .get(".TableInteractive-cellWrapper--lastColumn")
        .contains("1")
        .should("not.exist");
      cy.get(".TableInteractive-cellWrapper--lastColumn").last().contains("3");

      // Notebook filter
      cy.get(".Icon-notebook").click();
      cy.wait(2000).findByText("Summarize").click();
      cy.findByText("Count of rows").click();
      cy.findByText("Visualize").click();
      cy.findByText("18,760").should("not.exist");
      cy.findByText("10");
    });

    it("should be sandboxed with a filter (on a saved JOINed question)", () => {
      cy.visit("/question/5");

      cy.wait(2000)
        .get(".TableInteractive-cellWrapper--firstColumn")
        .should("have.length", 11);
    });

    it("should be sandbox with a filter (after applying a filter to a JOINed question)", () => {
      cy.visit("/question/5");

      // Notebook filter
      cy.get(".Icon-notebook").click();
      cy.wait(2000).findByText("Filter").click();
      cy.findAllByText("Total").last().click();
      cy.findByText("Equal to").click();
      cy.findByText("Greater than").click();
      cy.findByPlaceholderText("Enter a number").type("100");
      cy.findByText("Add filter").click();
      cy.findByText("Visualize").click();
      cy.wait(2000)
        .get(".TableInteractive-cellWrapper--firstColumn")
        .should("have.length", 7);
    });

    it("should filter categories on saved SQL question (for a new question - column number)", () => {
      cy.visit("/question/new?database=1&table=3");
      cy.get(".TableInteractive-cellWrapper--firstColumn").should(
        "have.length",
        2,
      );
    });

    it("should filter categories on saved SQL question (for a new question - row number)", () => {
      cy.visit("/question/new?database=1&table=3");
      cy.get(".TableInteractive-headerCellData").should("have.length", 4);
    });
  });
});
