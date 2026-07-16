import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AdminOrder } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";
import { AdminOrdersPage } from "./AdminOrdersPage";

const SHIP = "\u53d1\u8d27";
const SHIP_DIALOG = "\u8ba2\u5355\u53d1\u8d27";
const CONFIRM_SHIP = "\u786e\u8ba4\u53d1\u8d27";
const CARRIER = "\u627f\u8fd0\u5546";
const TRACKING_NO = "\u8fd0\u5355\u53f7";
const SHIPPED = "\u5df2\u53d1\u8d27";
const SF = "\u987a\u4e30\u901f\u8fd0";

const paidOrder: AdminOrder = {
  orderNo: "ORD-20260716-001",
  username: "linmu",
  status: "PAID",
  paymentStatus: "PAID",
  totalAmount: 699,
  itemCount: 1,
  createdAt: "2026-07-16T09:00:00Z",
  availableActions: ["SHIP"],
  addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u897f\u6e56\u533a\u6587\u4e00\u8def 188 \u53f7"
};

const unpaidOrder: AdminOrder = {
  orderNo: "ORD-20260716-002",
  username: "qingmu",
  status: "UNPAID",
  paymentStatus: "UNPAID",
  totalAmount: 299,
  itemCount: 1,
  createdAt: "2026-07-16T10:00:00Z",
  availableActions: [],
  addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u4f59\u676d\u533a"
};

describe("AdminOrdersPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("only exposes shipment action for paid shippable orders", async () => {
    vi.spyOn(api, "adminOrders").mockResolvedValue([paidOrder, unpaidOrder]);
    const shipOrder = vi.spyOn(api, "adminShipOrder").mockResolvedValue({
      ...paidOrder,
      status: "SHIPPED",
      availableActions: [],
      shipment: { carrier: SF, trackingNo: "SF123456789" }
    });

    render(<AdminOrdersPage />);

    const paidRow = (await screen.findByText("ORD-20260716-001")).closest("tr");
    const unpaidRow = screen.getByText("ORD-20260716-002").closest("tr");
    expect(paidRow).not.toBeNull();
    expect(unpaidRow).not.toBeNull();
    expect(within(paidRow!).getByRole("button", { name: SHIP })).toBeVisible();
    expect(within(unpaidRow!).queryByRole("button", { name: SHIP })).not.toBeInTheDocument();

    fireEvent.click(within(paidRow!).getByRole("button", { name: SHIP }));
    const dialog = screen.getByRole("dialog", { name: SHIP_DIALOG });
    const confirmButton = within(dialog).getByRole("button", { name: CONFIRM_SHIP });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText(CARRIER), { target: { value: SF } });
    expect(confirmButton).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(TRACKING_NO), { target: { value: "SF123456789" } });
    expect(confirmButton).toBeEnabled();
    fireEvent.click(confirmButton);

    await waitFor(() => expect(shipOrder).toHaveBeenCalledWith("ORD-20260716-001", SF, "SF123456789"));
    const updatedRow = screen.getByText("ORD-20260716-001").closest("tr");
    expect(within(updatedRow!).getByText(SHIPPED)).toBeVisible();
    expect(within(updatedRow!).queryByRole("button", { name: SHIP })).not.toBeInTheDocument();
  });
});
