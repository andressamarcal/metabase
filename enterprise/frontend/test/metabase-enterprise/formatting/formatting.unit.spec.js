import { render } from "@testing-library/react";

import "metabase-enterprise/formatting";
import { formatUrl } from "metabase/lib/formatting";

describe("formatting", () => {
  describe("formatUrl", () => {
    it("should use link template", () => {
      const link_template = "http://example.com/{{COL1}}";
      const clicked = {
        origin: {
          cols: [{ name: "COL1" }, { name: "COL2" }, { name: "COL3" }],
          row: ["a", "b", "c"],
        },
      };
      const options = { link_template, clicked };
      expect(formatUrl("some value", options)).toEqual("http://example.com/a");
    });

    it("should render a link with templated text", () => {
      const origin = {
        cols: [{ name: "COL1" }, { name: "COL2" }, { name: "COL3" }],
        row: ["a", "b", "c"],
      };
      const options = {
        link_template: "http://example.com/{{COL1}}",
        link_text: "foobar - {{COL2}}",
        clicked: { origin },
        jsx: true,
        rich: true,
      };
      const { getByText } = render(formatUrl("some value", options));
      expect(getByText("foobar - b").getAttribute("href")).toEqual(
        "http://example.com/a",
      );
    });
  });
});
