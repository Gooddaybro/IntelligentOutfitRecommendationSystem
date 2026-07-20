import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { ProfilePreferencesPage } from "./ProfilePreferencesPage";

vi.mock("../shared/api/client", () => ({
  api: {
    profile: vi.fn(),
    bodyData: vi.fn(),
    preferences: vi.fn(),
    updateProfile: vi.fn(),
    updateBodyData: vi.fn(),
    updateBodyMeasurements: vi.fn(),
    updatePreferences: vi.fn()
  }
}));

const mockedApi = vi.mocked(api);

describe("ProfilePreferencesPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockedApi.profile.mockResolvedValue({
      userId: 1001,
      nickname: "Alex",
      avatarUrl: "https://example.com/avatar.png",
      gender: "male",
      birthday: "1998-05-20"
    });
    mockedApi.bodyData.mockResolvedValue({
      userId: 1001,
      heightCm: 178.5,
      weightKg: 68,
      gender: "male",
      shoulderWidthCm: 44,
      bustCm: 92,
      waistCm: 76,
      hipCm: 90,
      preferredFit: "regular"
    });
    mockedApi.preferences.mockResolvedValue({
      userId: 1001,
      preferredStyles: ["commute", "minimal"],
      preferredColors: ["black", "white"],
      dislikedColors: ["orange"],
      preferredCategories: ["外套"],
      budgetMin: 100,
      budgetMax: 500
    });
    mockedApi.updateProfile.mockImplementation(async (request) => ({ userId: 1001, ...request }));
    mockedApi.updateBodyData.mockImplementation(async (request) => ({ userId: 1001, ...request }));
    mockedApi.updateBodyMeasurements.mockImplementation(async (request) => ({
      ...(await mockedApi.bodyData()),
      ...request
    }));
    mockedApi.updatePreferences.mockImplementation(async (request) => ({ userId: 1001, ...request }));
  });

  it("loads current profile, body data, and shopping preferences", async () => {
    render(<ProfilePreferencesPage />);

    expect(await screen.findByDisplayValue("Alex")).toBeInTheDocument();
    const page = screen.getByTestId("profile-page");
    expect(page).toHaveClass("profile-wardrobe-page");
    expect(page).not.toHaveClass("noir-page");
    expect(screen.getByDisplayValue("178.5")).toBeInTheDocument();
    expect(screen.getByDisplayValue("commute, minimal")).toBeInTheDocument();
    expect(mockedApi.profile).toHaveBeenCalledTimes(1);
    expect(mockedApi.bodyData).toHaveBeenCalledTimes(1);
    expect(mockedApi.preferences).toHaveBeenCalledTimes(1);
  });

  it("saves profile and body data through the current-user endpoints", async () => {
    render(<ProfilePreferencesPage />);
    await screen.findByDisplayValue("Alex");

    fireEvent.change(screen.getByTestId("profile-nickname"), { target: { value: "Alex Chen" } });
    fireEvent.click(screen.getByTestId("profile-save"));

    await waitFor(() =>
      expect(mockedApi.updateProfile).toHaveBeenCalledWith({
        nickname: "Alex Chen",
        avatarUrl: "https://example.com/avatar.png",
        gender: "male",
        birthday: "1998-05-20"
      })
    );
    expect(await screen.findByText("基础资料已保存")).toBeInTheDocument();

    fireEvent.change(screen.getByTestId("body-height-cm"), { target: { value: "180" } });
    fireEvent.change(screen.getByTestId("body-preferred-fit"), { target: { value: "loose" } });
    fireEvent.click(screen.getByTestId("body-save"));

    await waitFor(() =>
      expect(mockedApi.updateBodyMeasurements).toHaveBeenCalledWith({
        heightCm: 180,
        weightKg: 68
      })
    );
    expect(mockedApi.updateBodyData).not.toHaveBeenCalled();
  });

  it("uses labelled native dark controls for gender and birthday", async () => {
    render(<ProfilePreferencesPage />);
    await screen.findByDisplayValue("Alex");

    expect(screen.getByTestId("profile-gender")).toHaveAttribute("data-native-dark-control", "true");
    expect(screen.getByTestId("profile-birthday")).toHaveAttribute("data-native-dark-control", "true");
  });

  it("normalizes comma separated preference lists before saving", async () => {
    render(<ProfilePreferencesPage />);
    await screen.findByDisplayValue("commute, minimal");

    fireEvent.change(screen.getByTestId("preferences-styles"), { target: { value: "commute, minimal, commute" } });
    fireEvent.change(screen.getByTestId("preferences-budget-min"), { target: { value: "120" } });
    fireEvent.change(screen.getByTestId("preferences-budget-max"), { target: { value: "650" } });
    fireEvent.click(screen.getByTestId("preferences-save"));

    await waitFor(() =>
      expect(mockedApi.updatePreferences).toHaveBeenCalledWith({
        preferredStyles: ["commute", "minimal"],
        preferredColors: ["black", "white"],
        dislikedColors: ["orange"],
        preferredCategories: ["外套"],
        budgetMin: 120,
        budgetMax: 650
      })
    );
    expect(await screen.findByText("穿衣偏好已保存")).toBeInTheDocument();
  });

  it("blocks invalid budget ranges before calling the API", async () => {
    render(<ProfilePreferencesPage />);
    await screen.findByDisplayValue("commute, minimal");

    fireEvent.change(screen.getByTestId("preferences-budget-min"), { target: { value: "800" } });
    fireEvent.change(screen.getByTestId("preferences-budget-max"), { target: { value: "500" } });
    fireEvent.click(screen.getByTestId("preferences-save"));

    expect(await screen.findByText("预算下限不能大于预算上限")).toBeInTheDocument();
    expect(mockedApi.updatePreferences).not.toHaveBeenCalled();
  });
});
