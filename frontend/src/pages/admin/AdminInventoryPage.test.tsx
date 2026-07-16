import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AdminSku } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";
import { AdminInventoryPage } from "./AdminInventoryPage";

const lowStockSku: AdminSku = {
  skuId: 2001,
  skuCode: "PUFFER-IVORY-M",
  spuId: 1001,
  productName: "轻量通勤羽绒服",
  color: "米白",
  size: "M",
  salePrice: 699,
  availableStock: 3,
  lowStockThreshold: 5,
  status: "ACTIVE"
};

describe("AdminInventoryPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("标记低库存，并在填写目标库存和原因后更新调整摘要", async () => {
    vi.spyOn(api, "adminInventory").mockResolvedValue([lowStockSku]);
    const adjustInventory = vi.spyOn(api, "adminAdjustInventory").mockResolvedValue({
      ...lowStockSku,
      availableStock: 12,
      lastAdjustment: {
        beforeStock: 3,
        afterStock: 12,
        reason: "到货入库",
        operator: "水木管理员",
        adjustedAt: "2026-07-16T10:00:00Z"
      }
    });

    render(<AdminInventoryPage />);

    const skuRow = (await screen.findByText("PUFFER-IVORY-M")).closest("tr");
    expect(skuRow).not.toBeNull();
    expect(within(skuRow!).getByText("库存预警")).toBeVisible();

    fireEvent.click(within(skuRow!).getByRole("button", { name: "调整库存" }));
    const dialog = screen.getByRole("dialog", { name: "调整库存" });
    const confirmButton = within(dialog).getByRole("button", { name: "确认调整" });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(within(dialog).getByLabelText("目标库存"), { target: { value: "12" } });
    expect(confirmButton).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText("调整原因"), { target: { value: "到货入库" } });
    expect(confirmButton).toBeEnabled();
    fireEvent.click(confirmButton);

    await waitFor(() => expect(adjustInventory).toHaveBeenCalledWith(2001, 12, "到货入库"));
    const updatedRow = screen.getByText("PUFFER-IVORY-M").closest("tr");
    expect(within(updatedRow!).getByText("12")).toBeVisible();
    expect(within(updatedRow!).getByText(/到货入库/)).toBeVisible();
    expect(within(updatedRow!).getByText("库存正常")).toBeVisible();
  });
});
