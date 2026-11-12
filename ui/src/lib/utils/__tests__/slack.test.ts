import { slackDeepLink } from "../slack";

describe("slackDeepLink", () => {
  it("links to the message by channel and timestamp, with the timestamp's dot removed", () => {
    expect(slackDeepLink("C123", "1710147600.000100")).toBe("https://slack.com/archives/C123/p1710147600000100");
  });
});
