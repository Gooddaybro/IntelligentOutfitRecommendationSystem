import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import type { Address, OrderResponse } from "../shared/api/types";
import { CheckoutPage } from "./CheckoutPage";

const addresses: Address[] = [
  { id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true },
  { id: 2, recipientName: "青木", phone: "13900000000", province: "浙江省", city: "杭州市", district: "余杭区", detail: "余杭塘路 99 号" }
];

const preview = {
  items: [],
  merchandiseAmount: 699,
  shippingAmount: 0,
  discountAmount: 0,
  payableAmount: 699,
  invalidReasons: []
};

const order: OrderResponse = {
  orderNo: "ORD1",
  status: "UNPAID",
  totalAmount: 699,
  items: [],
  createdAt: "2026-08-23T10:00:00"
};

describe("CheckoutPage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("展示地址和由接口计算的应付金额", async () => {
    mockCheckout();
    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutPage onOrderCreated={vi.fn()} /></MemoryRouter>);
    expect(await screen.findByText(/文一路 88 号/)).toBeVisible();
    expect(screen.getAllByText("¥699.00")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "提交订单" })).toBeEnabled();
  });

  it("直接展示服务端返回的商品行金额", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue(addresses);
    vi.spyOn(api, "checkoutPreview").mockResolvedValue({
      ...preview,
      items: [{
        skuId: 11,
        spuId: 1,
        skuCode: "SKU-11",
        spuCode: "SPU-1",
        name: "服务端计价商品",
        categoryName: "上装",
        salePrice: 10,
        quantity: 2,
        lineAmount: 19.99
      }]
    });

    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutPage onOrderCreated={vi.fn()} /></MemoryRouter>);

    expect(await screen.findByText("¥19.99")).toBeVisible();
    expect(screen.queryByText("¥20.00")).not.toBeInTheDocument();
  });

  it("网络失败后人工重试复用同一提交意图的幂等键", async () => {
    mockCheckout();
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValue("11111111-1111-4111-8111-111111111111");
    const createOrder = vi.spyOn(api, "createOrder")
      .mockRejectedValueOnce(new Error("网络不可用"))
      .mockResolvedValueOnce(order);
    const onOrderCreated = vi.fn();
    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutPage onOrderCreated={onOrderCreated} /></MemoryRouter>);

    fireEvent.click(await screen.findByRole("button", { name: "提交订单" }));
    expect(await screen.findByText("网络不可用")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "提交订单" }));

    await waitFor(() => expect(onOrderCreated).toHaveBeenCalledWith(order));
    expect(createOrder).toHaveBeenNthCalledWith(1, [11], 1, "11111111-1111-4111-8111-111111111111");
    expect(createOrder).toHaveBeenNthCalledWith(2, [11], 1, "11111111-1111-4111-8111-111111111111");
  });

  it("修改地址后生成新的幂等键", async () => {
    mockCheckout();
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValueOnce("11111111-1111-4111-8111-111111111111")
      .mockReturnValueOnce("22222222-2222-4222-8222-222222222222");
    const createOrder = vi.spyOn(api, "createOrder")
      .mockRejectedValueOnce(new Error("网络不可用"))
      .mockResolvedValueOnce(order);
    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutPage onOrderCreated={vi.fn()} /></MemoryRouter>);

    fireEvent.click(await screen.findByRole("button", { name: "提交订单" }));
    expect(await screen.findByText("网络不可用")).toBeVisible();
    fireEvent.click(screen.getByRole("radio", { name: /青木/ }));
    fireEvent.click(screen.getByRole("button", { name: "提交订单" }));

    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder).toHaveBeenNthCalledWith(2, [11], 2, "22222222-2222-4222-8222-222222222222");
  });

  it("修改商品选择后生成新的幂等键", async () => {
    mockCheckout();
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValueOnce("11111111-1111-4111-8111-111111111111")
      .mockReturnValueOnce("22222222-2222-4222-8222-222222222222");
    const createOrder = vi.spyOn(api, "createOrder")
      .mockRejectedValueOnce(new Error("网络不可用"))
      .mockResolvedValueOnce(order);
    render(<MemoryRouter initialEntries={["/app/checkout?skuIds=11"]}><CheckoutWithSkuChange /></MemoryRouter>);

    fireEvent.click(await screen.findByRole("button", { name: "提交订单" }));
    expect(await screen.findByText("网络不可用")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "更换商品" }));
    fireEvent.click(screen.getByRole("button", { name: "提交订单" }));

    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder).toHaveBeenNthCalledWith(2, [12], 1, "22222222-2222-4222-8222-222222222222");
  });
});

function mockCheckout() {
  vi.spyOn(api, "addresses").mockResolvedValue(addresses);
  vi.spyOn(api, "checkoutPreview").mockResolvedValue(preview);
}

function CheckoutWithSkuChange() {
  const navigate = useNavigate();
  return <>
    <button onClick={() => navigate("/app/checkout?skuIds=12")}>更换商品</button>
    <CheckoutPage onOrderCreated={vi.fn()} />
  </>;
}
