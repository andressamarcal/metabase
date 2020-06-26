import { signIn } from "../../../../../frontend/test/__support__/cypress";

// **** Delete after merged with master
Cypress.on("uncaught:exception", (err, runnable) => false);
export function withSampleDataset(f) {
  cy.request("GET", "/api/database/1/metadata").then(({ body }) => {
    const SAMPLE_DATASET = {};
    for (const table of body.tables) {
      const fields = {};
      for (const field of table.fields) {
        fields[field.name] = field.id;
      }
      SAMPLE_DATASET[table.name] = fields;
      SAMPLE_DATASET[table.name + "_ID"] = table.id;
    }
    f(SAMPLE_DATASET);
  });
}

// Change settings like theme colors, sandbox permissions, etc.

export function changeThemeColor(location, colorhex) {
  cy.get("td")
    .eq(location)
    .click();
  cy.get(`div[title='#${colorhex}']`).click();
  cy.findByText("Done").click();
}
export function changePermissionsforSandbox(
  location,
  column,
  user_attribute,
  first,
) {
  cy.findByText("Data permissions");
  cy.get(".ReactVirtualized__Grid__innerScrollContainer")
    .children()
    .eq(location)
    .click();
  cy.findByText("Grant sandboxed access").click();
  if (first == "first") {
    cy.findByText("Change").click();
  }
  cy.get(".Icon-chevrondown")
    .first()
    .click();
  cy.findByText(column).click();
  cy.get(".Icon-chevrondown")
    .last()
    .click();
  cy.findAllByText(user_attribute)
    .last()
    .click();
  cy.findByText("Save").click();
}

// Generate information like questions, dashboards, etc.

export function generateQuestions(users) {
  users.forEach(user => {
    signIn(user);

    withSampleDataset(({ PRODUCTS }) => {
      cy.request("POST", `/api/card`, {
        name: `${user} test q`,
        dataset_query: {
          type: "native",
          native: {
            query: "SELECT * FROM products WHERE {{ID}}",
            "template-tags": {
              ID: {
                id: "6b8b10ef-0104-1047-1e1b-2492d5954322",
                name: "ID",
                display_name: "ID",
                type: "dimension",
                dimension: ["field-id", PRODUCTS.CREATED_AT],
                "widget-type": "category",
                default: null,
              },
            },
          },
          database: 1,
        },
        display: "scalar",
        description: null,
        visualization_settings: {},
        collection_id: null,
        result_metadata: null,
        metadata_checksum: null,
      });
    });
  });
}
export function generateDashboards(users) {
  users.forEach(user => {
    signIn(user);
    cy.visit("/");
    cy.get(".Icon-add").click();
    cy.findByText("New dashboard").click();
    cy.findByLabelText("Name").type(user + " test dash");
    cy.get(".Icon-chevrondown").click();
    cy.findAllByText("Our analytics")
      .last()
      .click();
    cy.findByText("Create").click();
  });
}
