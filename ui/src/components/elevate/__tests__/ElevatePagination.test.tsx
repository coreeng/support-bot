import { render, screen } from "@testing-library/react";
import { ElevatePagination } from "../ElevatePagination";

describe("ElevatePagination", () => {
  it("wraps in narrow containers and disables stale navigation while busy", () => {
    const { container } = render(<ElevatePagination page={1234} totalPages={9999} busy onPageChange={jest.fn()} />);

    expect(container.firstChild).toHaveClass("flex-wrap", "px-3", "sm:px-6");
    expect(container.firstChild).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Updating…")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("shows the requested page when idle", () => {
    const { container } = render(<ElevatePagination page={1234} totalPages={9999} onPageChange={jest.fn()} />);

    expect(container.firstChild).toHaveTextContent("Page 1235 of 9999");
    expect(screen.getByRole("button", { name: "Previous" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Next" })).toBeEnabled();
  });
});
