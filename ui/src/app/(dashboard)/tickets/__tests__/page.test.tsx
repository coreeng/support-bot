import { redirect } from "next/navigation";
import Tickets from "../page";

jest.mock("next/navigation", () => ({
  redirect: jest.fn(),
}));

const mockRedirect = redirect as jest.MockedFunction<typeof redirect>;

describe("Tickets redirect page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("redirects to the home page when there are no query params", async () => {
    await Tickets({ searchParams: Promise.resolve({}) });

    expect(mockRedirect).toHaveBeenCalledTimes(1);
    expect(mockRedirect).toHaveBeenCalledWith("/");
  });

  it("forwards the query string so bookmarked filters keep working", async () => {
    await Tickets({ searchParams: Promise.resolve({ teamFilter: "platform", page: "2" }) });

    expect(mockRedirect).toHaveBeenCalledWith("/?teamFilter=platform&page=2");
  });

  it("appends each element of a repeated query param", async () => {
    await Tickets({ searchParams: Promise.resolve({ tag: ["a", "b"], status: "opened" }) });

    expect(mockRedirect).toHaveBeenCalledWith("/?tag=a&tag=b&status=opened");
  });

  it("skips params without a value", async () => {
    await Tickets({ searchParams: Promise.resolve({ teamFilter: undefined, page: "3" }) });

    expect(mockRedirect).toHaveBeenCalledWith("/?page=3");
  });
});
