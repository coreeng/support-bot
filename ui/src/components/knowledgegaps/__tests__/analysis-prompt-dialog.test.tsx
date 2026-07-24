import { render, screen } from "@testing-library/react";
import * as hooks from "../../../lib/hooks";
import AnalysisPromptDialog from "../AnalysisPromptDialog";

jest.mock("../../../lib/hooks", () => ({
  useAnalysisPrompt: jest.fn(),
}));

const mockUseAnalysisPrompt = hooks.useAnalysisPrompt as jest.MockedFunction<typeof hooks.useAnalysisPrompt>;

describe("AnalysisPromptDialog", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders the prompt text when loaded", () => {
    mockUseAnalysisPrompt.mockReturnValue({
      data: { prompt: "You are an expert Platform Enablement analyst." },
      isLoading: false,
      error: null,
    } as any);

    render(<AnalysisPromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Analysis Prompt")).toBeInTheDocument();
    expect(screen.getByText("You are an expert Platform Enablement analyst.")).toBeInTheDocument();
  });

  it("shows a loading state while fetching", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isLoading: true, error: null } as any);

    render(<AnalysisPromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Loading prompt...")).toBeInTheDocument();
  });

  it("shows an error state when the fetch fails", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isLoading: false, error: new Error("boom") } as any);

    render(<AnalysisPromptDialog open onOpenChange={jest.fn()} />);

    expect(screen.getByText("Failed to load analysis prompt. Please try again.")).toBeInTheDocument();
  });

  it("does not fetch or render content while closed", () => {
    mockUseAnalysisPrompt.mockReturnValue({ data: undefined, isLoading: false, error: null } as any);

    render(<AnalysisPromptDialog open={false} onOpenChange={jest.fn()} />);

    expect(mockUseAnalysisPrompt).toHaveBeenCalledWith(false);
    expect(screen.queryByText("Analysis Prompt")).not.toBeInTheDocument();
  });
});
