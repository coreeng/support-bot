import { fireEvent, render, screen } from "@testing-library/react";
import { ElevateDataTable } from "../ElevateDataTable";

describe("ElevateDataTable", () => {
  it("paginates long synchronized collections", () => {
    const items = Array.from({ length: 11 }, (_, index) => ({ id: `${index + 1}`, name: `Product ${index + 1}` }));
    render(
      <ElevateDataTable
        title="Products"
        description="Synchronized products"
        caption="Products from Elevate"
        items={items}
        columns={[{ key: "name", header: "Name", render: (item) => item.name }]}
        rowKey={(item) => item.id}
        emptyTitle="No products"
        emptyDescription="Nothing to show"
      />
    );

    expect(screen.getByText("Product 1")).toBeInTheDocument();
    expect(screen.queryByText("Product 11")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Product 11")).toBeInTheDocument();
    expect(screen.getByLabelText("Pagination status")).toHaveTextContent("Page 2 of 2");
  });
});
