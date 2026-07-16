import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ProfileCenterPage } from "./ProfileCenterPage";

describe("ProfileCenterPage", () => {
  it("提供资料、衣橱、收藏、地址和安全五个入口", () => {
    render(<MemoryRouter initialEntries={["/app/profile/account"]}><Routes><Route path="/app/profile" element={<ProfileCenterPage />}><Route path="account" element={<p>账户内容</p>} /></Route></Routes></MemoryRouter>);
    for (const name of ["个人资料", "衣橱画像", "我的收藏", "收货地址", "账户安全"]) expect(screen.getByRole("link", { name })).toBeVisible();
    expect(screen.getByText("账户内容")).toBeVisible();
  });
});
