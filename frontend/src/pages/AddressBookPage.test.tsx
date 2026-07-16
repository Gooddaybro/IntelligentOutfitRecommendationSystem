import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { AddressBookPage } from "./AddressBookPage";

describe("AddressBookPage", () => {
  afterEach(() => vi.restoreAllMocks());
  it("展示已保存地址和新增入口", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([{ id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }]);
    render(<AddressBookPage />);
    expect(await screen.findByText(/文一路 88 号/)).toBeVisible();
    expect(screen.getByRole("button", { name: "新增地址" })).toBeVisible();
  });
});
