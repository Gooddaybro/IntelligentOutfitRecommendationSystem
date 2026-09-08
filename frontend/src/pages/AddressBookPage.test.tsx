import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../shared/api/client";
import { AddressBookPage } from "./AddressBookPage";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe("AddressBookPage", () => {
  afterEach(() => vi.restoreAllMocks());

  const firstAddress = { id: 1, recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true };
  const secondAddress = { id: 2, recipientName: "小林", phone: "13900000000", province: "上海市", city: "上海市", district: "徐汇区", detail: "漕溪北路 1 号", isDefault: false };

  it("展示已保存地址和新增入口", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress]);
    render(<AddressBookPage />);
    expect(await screen.findByText(/文一路 88 号/)).toBeVisible();
    expect(screen.getByRole("button", { name: "新增地址" })).toBeVisible();
  });

  it("初始加载期间不允许发起地址操作", async () => {
    const loading = deferred<typeof firstAddress[]>();
    vi.spyOn(api, "addresses").mockReturnValue(loading.promise);
    const createAddress = vi.spyOn(api, "createAddress");
    render(<AddressBookPage />);

    const add = screen.getByRole("button", { name: "新增地址" });
    expect(add).toBeDisabled();
    fireEvent.click(add);
    expect(createAddress).not.toHaveBeenCalled();

    loading.resolve([firstAddress]);
    expect(await screen.findByText(/文一路 88 号/)).toBeVisible();
    expect(add).toBeEnabled();
  });

  it("创建地址并使用服务端返回的完整地址列表", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress]);
    const createAddress = vi.spyOn(api, "createAddress").mockResolvedValue([firstAddress, secondAddress]);
    render(<AddressBookPage />);
    await screen.findByText(/文一路 88 号/);

    fireEvent.click(screen.getByRole("button", { name: "新增地址" }));
    fireEvent.change(screen.getByLabelText("收货人"), { target: { value: "小林" } });
    fireEvent.change(screen.getByLabelText("手机号"), { target: { value: "13900000000" } });
    fireEvent.change(screen.getByLabelText("省份"), { target: { value: "上海市" } });
    fireEvent.change(screen.getByLabelText("城市"), { target: { value: "上海市" } });
    fireEvent.change(screen.getByLabelText("区县"), { target: { value: "徐汇区" } });
    fireEvent.change(screen.getByLabelText("详细地址"), { target: { value: "漕溪北路 1 号" } });
    fireEvent.click(screen.getByRole("button", { name: "保存地址" }));

    await waitFor(() => expect(createAddress).toHaveBeenCalledWith({ recipientName: "小林", phone: "13900000000", province: "上海市", city: "上海市", district: "徐汇区", detail: "漕溪北路 1 号" }));
    expect(await screen.findByText(/漕溪北路 1 号/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "保存地址" })).not.toBeInTheDocument();
  });

  it("编辑地址时预填表单并调用更新接口", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress]);
    const updateAddress = vi.spyOn(api, "updateAddress").mockResolvedValue([{ ...firstAddress, detail: "文一路 99 号" }]);
    render(<AddressBookPage />);
    await screen.findByText(/文一路 88 号/);

    fireEvent.click(screen.getByRole("button", { name: "编辑林木的地址" }));
    expect(screen.getByLabelText("收货人")).toHaveValue("林木");
    fireEvent.change(screen.getByLabelText("详细地址"), { target: { value: "文一路 99 号" } });
    fireEvent.click(screen.getByRole("button", { name: "保存地址" }));

    await waitFor(() => expect(updateAddress).toHaveBeenCalledWith(1, { recipientName: "林木", phone: "13800000000", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 99 号" }));
    expect(await screen.findByText(/文一路 99 号/)).toBeVisible();
  });

  it("删除地址并切换默认地址", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress, secondAddress]);
    const deleteAddress = vi.spyOn(api, "deleteAddress").mockResolvedValue([{ ...secondAddress, isDefault: true }]);
    const setDefaultAddress = vi.spyOn(api, "setDefaultAddress").mockResolvedValue([{ ...secondAddress, isDefault: true }, { ...firstAddress, isDefault: false }]);
    render(<AddressBookPage />);
    await screen.findByText(/文一路 88 号/);

    fireEvent.click(screen.getByRole("button", { name: "设为默认地址小林" }));
    await waitFor(() => expect(setDefaultAddress).toHaveBeenCalledWith(2));
    const secondArticle = screen.getByRole("button", { name: "编辑小林的地址" }).closest("article")!;
    const firstArticle = screen.getByRole("button", { name: "编辑林木的地址" }).closest("article")!;
    expect(secondArticle.parentElement?.firstElementChild).toBe(secondArticle);
    expect(within(secondArticle).getByText("默认")).toBeVisible();
    expect(within(firstArticle).queryByText("默认")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "删除林木的地址" }));
    await waitFor(() => expect(deleteAddress).toHaveBeenCalledWith(1));
    expect(screen.queryByText(/文一路 88 号/)).not.toBeInTheDocument();
  });

  it("展示加载与提交失败，并保留失败的表单", async () => {
    vi.spyOn(api, "addresses").mockRejectedValueOnce(new Error("加载失败")).mockResolvedValue([firstAddress]);
    const createAddress = vi.spyOn(api, "createAddress").mockRejectedValue(new Error("保存失败"));
    render(<AddressBookPage />);
    expect(await screen.findByText("加载失败")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "新增地址" }));
    for (const [label, value] of [["收货人", "小林"], ["手机号", "13900000000"], ["省份", "上海市"], ["城市", "上海市"], ["区县", "徐汇区"], ["详细地址", "漕溪北路 1 号"]]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("button", { name: "保存地址" }));

    await waitFor(() => expect(createAddress).toHaveBeenCalled());
    expect(await screen.findByText("保存失败")).toBeVisible();
    expect(screen.getByRole("button", { name: "保存地址" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "新增地址" })).toBeEnabled();
  });

  it("默认地址切换进行时阻止后续写入覆盖服务端列表", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress, secondAddress]);
    const pendingDefault = deferred<typeof firstAddress[]>();
    const setDefaultAddress = vi.spyOn(api, "setDefaultAddress").mockReturnValue(pendingDefault.promise);
    const createAddress = vi.spyOn(api, "createAddress");
    const updateAddress = vi.spyOn(api, "updateAddress");
    const deleteAddress = vi.spyOn(api, "deleteAddress");
    render(<AddressBookPage />);
    await screen.findByText(/文一路 88 号/);

    fireEvent.click(screen.getByRole("button", { name: "设为默认地址小林" }));
    await waitFor(() => expect(setDefaultAddress).toHaveBeenCalledWith(2));
    const add = screen.getByRole("button", { name: "新增地址" });
    const edit = screen.getByRole("button", { name: "编辑林木的地址" });
    const remove = screen.getByRole("button", { name: "删除林木的地址" });
    const setDefault = screen.getByRole("button", { name: "设为默认地址小林" });
    expect(add).toBeDisabled();
    expect(edit).toBeDisabled();
    expect(remove).toBeDisabled();
    expect(setDefault).toBeDisabled();
    fireEvent.click(add);
    fireEvent.click(edit);
    fireEvent.click(remove);
    fireEvent.click(setDefault);
    expect(createAddress).not.toHaveBeenCalled();
    expect(updateAddress).not.toHaveBeenCalled();
    expect(deleteAddress).not.toHaveBeenCalled();
    expect(setDefaultAddress).toHaveBeenCalledTimes(1);

    pendingDefault.resolve([{ ...secondAddress, isDefault: true }, { ...firstAddress, isDefault: false }]);
    await waitFor(() => expect(add).toBeEnabled());
  });

  it("提交进行时不允许打开或编辑另一份表单", async () => {
    vi.spyOn(api, "addresses").mockResolvedValue([firstAddress, secondAddress]);
    const pendingCreate = deferred<typeof firstAddress[]>();
    const createAddress = vi.spyOn(api, "createAddress").mockReturnValue(pendingCreate.promise);
    const deleteAddress = vi.spyOn(api, "deleteAddress");
    render(<AddressBookPage />);
    await screen.findByText(/文一路 88 号/);

    fireEvent.click(screen.getByRole("button", { name: "新增地址" }));
    for (const [label, value] of [["收货人", "新地址"], ["手机号", "13900000000"], ["省份", "上海市"], ["城市", "上海市"], ["区县", "徐汇区"], ["详细地址", "漕溪北路 1 号"]]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("button", { name: "保存地址" }));
    await waitFor(() => expect(createAddress).toHaveBeenCalledTimes(1));
    const add = screen.getByRole("button", { name: "新增地址" });
    const edit = screen.getByRole("button", { name: "编辑林木的地址" });
    const remove = screen.getByRole("button", { name: "删除林木的地址" });
    expect(screen.getByRole("button", { name: "保存地址" })).toBeDisabled();
    expect(screen.getByLabelText("收货人")).toBeDisabled();
    expect(screen.getByLabelText("手机号")).toBeDisabled();
    expect(screen.getByLabelText("省份")).toBeDisabled();
    expect(screen.getByLabelText("城市")).toBeDisabled();
    expect(screen.getByLabelText("区县")).toBeDisabled();
    expect(screen.getByLabelText("详细地址")).toBeDisabled();
    expect(add).toBeDisabled();
    expect(edit).toBeDisabled();
    expect(remove).toBeDisabled();
    fireEvent.click(add);
    fireEvent.click(edit);
    fireEvent.click(remove);
    expect(deleteAddress).not.toHaveBeenCalled();

    pendingCreate.resolve([{ id: 3, recipientName: "新地址", phone: "13900000000", province: "上海市", city: "上海市", district: "徐汇区", detail: "漕溪北路 1 号", isDefault: false }, firstAddress, secondAddress]);
    await waitFor(() => expect(screen.queryByRole("button", { name: "保存地址" })).not.toBeInTheDocument());
    expect(add).toBeEnabled();
  });
});
