import { fireEvent, render, screen } from "@testing-library/react";
import * as hooks from "../../../lib/hooks";
import PromptDialog from "../prompt-dialog";

jest.mock("next-auth/react", () => ({
  getCsrfToken: jest.fn(() => Promise.resolve("mock-csrf-token")),
  signOut: jest.fn(() => Promise.resolve()),
}));

// Spread the real module so the component keeps the genuine ApiError/isApiError.
jest.mock("../../../lib/hooks", () => ({
  ...jest.requireActual("../../../lib/hooks"),
  useAnalysisPrompt: jest.fn(),
  useSummaryPrompt: jest.fn(),
}));

const mockUseAnalysisPrompt = hooks.useAnalysisPrompt as jest.MockedFunction<typeof hooks.useAnalysisPrompt>;
const mockUseSummaryPrompt = hooks.useSummaryPrompt as jest.MockedFunction<typeof hooks.useSummaryPrompt>;

const idle = { data: undefined, isFetching: false, error: null } as any;

describe("PromptDialog", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAnalysisPrompt.mockReturnValue(idle);
    mockUseSummaryPrompt.mockReturnValue(idle);
  });

  it("defaults to the ticket classification prompt", () => {
    mockUseAnalysisPrompt.mockReturnValue({
      data: { prompt: "You are an expert Platform Enablement analyst." },
      isFetching: false,
      error: null,
    } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(mockUseAnalysisPrompt).toHaveBeenCalledWith(true);
    expect(mockUseSummaryPrompt).toHaveBeenCalledWith(false);
    expect(screen.getByText("You are an expert Platform Enablement analyst.")).toBeInTheDocument();
  });

  it("shows the summary prompt when switched via the dropdown", () => {
    mockUseSummaryPrompt.mockReturnValue({
      data: { prompt: "You write a concise summary of the window." },
      isFetching: false,
      error: null,
    } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);
    fireEvent.click(screen.getByTestId("prompt-dialog-kind"));
    fireEvent.click(screen.getByRole("option", { name: "Summary generation" }));

    expect(mockUseSummaryPrompt).toHaveBeenLastCalledWith(true);
    expect(mockUseAnalysisPrompt).toHaveBeenLastCalledWith(false);
    expect(screen.getByText("You write a concise summary of the window.")).toBeInTheDocument();
  });

  it("does not fetch either prompt while closed", () => {
    render(<PromptDialog open={false} onOpenChange={jest.fn()} />);

    expect(mockUseAnalysisPrompt).toHaveBeenCalledWith(false);
    expect(mockUseSummaryPrompt).toHaveBeenCalledWith(false);
  });

  it("shows a loading state while fetching", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isFetching: true, error: null } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Loading prompt...")).toBeInTheDocument();
  });

  it("shows the loading state instead of previously fetched data while refetching", () => {
    mockUseAnalysisPrompt.mockReturnValue({
      data: { prompt: "stale prompt from a previous open" },
      isFetching: true,
      error: null,
    } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Loading prompt...")).toBeInTheDocument();
    expect(screen.queryByText("stale prompt from a previous open")).not.toBeInTheDocument();
  });

  it("shows an error state when the fetch fails", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isFetching: false, error: new Error("boom") } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Failed to load the ticket classification prompt. Please try again.")).toBeInTheDocument();
  });

  it("shows permission copy for a 403", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isFetching: false, error: new hooks.ApiError(403) } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("You do not have permission to view the ticket classification prompt.")).toBeInTheDocument();
  });

  it("shows server-side copy when the backend reports the prompt failed to load", () => {
    mockUseAnalysisPrompt.mockReturnValue({
      data: undefined,
      isFetching: false,
      error: new hooks.ApiError(500, "ANALYSIS_PROMPT_LOAD_FAILED"),
    } as any);

    render(<PromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("The ticket classification prompt could not be loaded on the server.")).toBeInTheDocument();
  });
});
