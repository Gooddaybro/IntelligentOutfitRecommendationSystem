import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AdminUser } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";
import { AdminUsersPage } from "./AdminUsersPage";

const DISABLE = "\u7981\u7528";
const CONFIRM_DISABLE_USER = "\u786e\u8ba4\u7981\u7528\u7528\u6237";
const CONFIRM_DISABLE = "\u786e\u8ba4\u7981\u7528";
const DISABLED = "\u5df2\u7981\u7528";
const PASSWORD = "\u5bc6\u7801";

const activeUser: AdminUser = {
  userId: 10001,
  username: "linmu",
  nickname: "\u6797\u6728",
  email: "linmu@example.com",
  phone: "13800000000",
  status: "ACTIVE",
  registeredAt: "2026-07-01T09:00:00Z",
  orderCount: 3,
  paidAmount: 1299
};

describe("AdminUsersPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("does not expose password fields and disables users only after confirmation", async () => {
    vi.spyOn(api, "adminUsers").mockResolvedValue([activeUser]);
    const setStatus = vi.spyOn(api, "adminSetUserStatus").mockResolvedValue({ ...activeUser, status: "DISABLED" });

    render(<AdminUsersPage />);

    const row = (await screen.findByText("linmu")).closest("tr");
    expect(row).not.toBeNull();
    expect(screen.queryByText(PASSWORD)).not.toBeInTheDocument();
    expect(screen.queryByText(/password/i)).not.toBeInTheDocument();

    fireEvent.click(within(row!).getByRole("button", { name: DISABLE }));
    const dialog = screen.getByRole("dialog", { name: CONFIRM_DISABLE_USER });
    expect(dialog).toHaveTextContent("linmu");
    fireEvent.click(within(dialog).getByRole("button", { name: CONFIRM_DISABLE }));

    await waitFor(() => expect(setStatus).toHaveBeenCalledWith(10001, "DISABLED"));
    const updatedRow = screen.getByText("linmu").closest("tr");
    expect(within(updatedRow!).getByText(DISABLED)).toBeVisible();
  });
});
