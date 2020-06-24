import {
  restore,
  signIn,
  signOut,
  signInAsAdmin,
  USERS,
  signInAsNormalUser,
} from "../../../../../frontend/test/__support__/cypress";
import {
  generateQuestions,
  generateDashboards,
  year,
} from "../_support_/cypress";

describe("audit > auditing", () => {
  before(restore);
  const users = ["admin", "normal"];

  describe("Generate data to audit", () => {
    beforeEach(signOut);

    it("should create questions and dashboards", () => {
      generateQuestions(users);
      generateDashboards(users);
    });

    it("should view a dashboard", () => {
      signIn("nodata");
      cy.visit("/collection/root?type=dashboard");
      cy.wait(3000)
        .findByText(users[1] + " test dash")
        .click();

      cy.findByText("This dashboard is looking empty.");
      cy.findByText("My personal collection").should("not.exist");
    });

    it("should view old question and new question", () => {
      signIn("nodata");
      cy.visit("/collection/root?type");
      cy.wait(2000)
        .findByText("Orders, Count")
        .click();

      cy.findByText("18,760");

      cy.visit("/collection/root?type");
      cy.wait(2000)
        .findByText(users[0] + " test q")
        .click();

      cy.findByText("Category");
    });

    it("should download a question", () => {
      signInAsNormalUser();
      cy.visit("/question/3");
      cy.server();
      cy.get(".Icon-download").click();
      cy.request("POST", "/api/card/1/query/json");
    });
  });

  describe("See expected info on team member pages", () => {
    beforeEach(signInAsAdmin);

    const all_users = [
      USERS.admin,
      USERS.normal,
      USERS.nodata,
      USERS.nocollection,
      USERS.none,
    ];

    it("should load the Overview tab", () => {
      cy.visit("/admin/audit/members/overview");

      cy.findByText("Active members and new members per day");
      cy.findByText("No results!");
      cy.wait(1000)
        .get(".LineAreaBarChart")
        .first()
        .find("[width='0']")
        .should("have.length", 2);
      cy.get("svg")
        .last()
        .find("[width='0']")
        .should("have.length", 3);
    });

    it("should load the All Members tab", () => {
      cy.visit("/admin/audit/members/all");

      all_users.forEach(user => {
        cy.findByText(user.first_name + " " + user.last_name);
      });
      cy.get("tr")
        .last()
        .children()
        .eq(-2)
        .should("contain", year);
    });

    it("should load the Audit log", () => {
      cy.visit("/admin/audit/members/log");

      cy.findAllByText("Ad-hoc").should("have.length", 4);
      cy.findAllByText("Orders, Count").should("have.length", 1);
      cy.findAllByText("admin test q").should("have.length", 1);
      cy.findAllByText("Sample Dataset").should("have.length", 8);
      cy.findByText(users[1] + " test dash").should("not.exist");
      
      // *** Uncomment when page works correctly:
      // cy.findByText(users[1] + " test dash");
    });
  });

  describe("See expected info on data pages", () => {
    beforeEach(signInAsAdmin);

    it("should load both tabs in Databases", () => {
      // Overview tab
      cy.visit("/admin/audit/databases/overview");
      cy.findByText("Total queries and their average speed");
      cy.findByText("No results!").should("not.exist");
      cy.get(".voronoi");
      cy.get("rect");

      // All databases tab
      cy.visit("/admin/audit/databases/all");
      cy.findByPlaceholderText("Database name");
      cy.findByText("No results!").should("not.exist");
      cy.findByText("Sample Dataset");
      cy.findByText("Every hour");
    });

    it("should load both tabs in Schemas", () => {
      // Overview tab
      cy.visit("/admin/audit/schemas/overview");
      cy.get("svg").should("have.length", 2);
      cy.wait(1000).findAllByText("Sample Dataset PUBLIC");
      cy.findAllByText("No results!").should("not.exist");

      // All schemas tab
      cy.visit("/admin/audit/schemas/all");
      cy.findByText("PUBLIC");
      cy.findByText("Saved Queries");
    });

    it("should load both tabs in Tables", () => {
      // Overview tab
      cy.visit("/admin/audit/tables/overview");
      cy.findByText("Most-queried tables");
      cy.findAllByText("No results!").should("not.exist");
      // *** Should tables have the same titles?
      cy.wait(1000).findAllByText("Sample Dataset PUBLIC PRODUCTS");
      cy.get(".rowChart")
        .first()
        .find('[height="30"]')
        .should("have.length", 2);
      cy.get(".rowChart")
        .last()
        .find("[height='30']")
        .should("have.length", 2);

      // All tables tab
      cy.visit("/admin/audit/tables/all");
      cy.findByPlaceholderText("Table name");
      cy.findAllByText("PUBLIC").should("have.length", 4);
      cy.findByText("REVIEWS");
      cy.findByText("Reviews");
    });
  });

  describe("See expected info on item pages", () => {
    beforeEach(signInAsAdmin);

    it("should load both tabs in Questions", () => {
      // Overview tab
      cy.visit("/admin/audit/questions/overview");
      cy.findByText("Slowest queries");
      cy.findByText("Query views and speed per day");
      cy.findAllByText("No results!").should("not.exist");
      cy.get("svg").should("have.length", 3);
      cy.get("rect");
      cy.get(".voronoi");

      // All questions tab
      cy.visit("/admin/audit/questions/all");
      cy.findByPlaceholderText("Question name");
      cy.wait(1000)
        .findAllByText("Sample Dataset")
        .should("have.length", 5);
      cy.findByText("normal test q");
      cy.findByText("Orders, Count, Grouped by Created At (year)");
      cy.findByText("4").should("not.exist");
    });

    it("should load both tabs in Dashboards", () => {
      // Overview tab
      cy.visit("/admin/audit/dashboards/overview");
      cy.findByText("Most popular dashboards and their avg loading times");
      cy.findAllByText("Avg. Question Load Time (ms)");
      cy.findByText("normal test dash");
      cy.findByText("Orders");
      cy.findByText("Orders, Count").should("not.exist");

      // All dashboards tab
      cy.visit("/admin/audit/dashboards/all");
      cy.findByPlaceholderText("Dashboard name");
      cy.findByText("admin test dash");
      cy.findByText(USERS.normal.first_name + " " + USERS.normal.last_name);
      cy.get("tr")
        .eq(1)
        .children()
        .last()
        .should("contain", year);
    });

    it("should load both tabs in Downloads", () => {
      // Overview tab
      cy.visit("/admin/audit/downloads/overview");
      cy.findByText("No results!").should("not.exist");
      cy.findByText("Largest downloads in the last 30 days");
      cy.findByText(USERS.normal.first_name + " " + USERS.normal.last_name);

      // All downloads tab
      cy.visit("/admin/audit/downloads/all");
      cy.wait(1000)
        .findByText("No results")
        .should("not.exist");
      cy.get("tr")
        .last()
        .children()
        .first()
        .should("contain", year);
      cy.findByText("GUI");
    });
  });
});
