import { mapTicket } from "../map-ticket";

describe("mapTicket team mapping", () => {
  it("carries both the identity (name = code) and the display label for the ticket team", () => {
    const result = mapTicket({
      id: 1,
      team: { code: "pe", label: "PE Core", active: true },
      escalations: [],
    });

    expect(result.team).toEqual({ name: "pe", label: "PE Core", active: true });
  });

  it("carries the label for escalation teams and preserves the retired flag", () => {
    const result = mapTicket({
      id: 2,
      team: { code: "old-team", label: "Old Team", active: false },
      escalations: [{ id: 7, team: { code: "esc", label: "Escalation One", active: true } }],
    });

    expect(result.team).toEqual({ name: "old-team", label: "Old Team", active: false });
    expect(result.escalations[0].team).toEqual({ name: "esc", label: "Escalation One", active: true });
  });

  it("falls back across code and label when one is missing", () => {
    const onlyCode = mapTicket({ id: 3, team: { code: "wow" }, escalations: [] });
    expect(onlyCode.team).toEqual({ name: "wow", label: "wow", active: undefined });

    const onlyLabel = mapTicket({ id: 4, team: { label: "Only Label" }, escalations: [] });
    expect(onlyLabel.team).toEqual({ name: "Only Label", label: "Only Label", active: undefined });
  });

  it("returns a null team when the ticket has no team", () => {
    const result = mapTicket({ id: 5, escalations: [] });
    expect(result.team).toBeNull();
  });
});
