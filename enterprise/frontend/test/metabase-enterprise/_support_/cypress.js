import {
  signIn,
  openProductsTable,
  signOut,
} from "../../../../../frontend/test/__support__/cypress";

export const year = new Date().getFullYear();

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

    openProductsTable();
    cy.wait(2000)
      .findAllByText("Summarize")
      .first()
      .click({ force: true });
    cy.wait(2000)
      .get(".List-section")
      .eq(users.indexOf(user) + 1)
      .click();
    cy.findByText("Done").click();

    cy.findByText("Save").click();
    cy.findByLabelText("Name")
      .clear()
      .type(user + " test q");
    cy.get(".Icon-chevrondown").click();
    cy.wait(500)
      .findAllByText("Our analytics")
      .last()
      .click();
    cy.findAllByText("Save")
      .last()
      .click();
    cy.findByText("Not now").click();

    signOut();
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
