import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { CustomerShell } from "./CustomerShell";

describe("CustomerShell", () => {
  it("显示桌面商城导航和五项移动端导航", () => {
    render(
      <MemoryRouter initialEntries={["/app/home"]}>
        <Routes>
          <Route element={<CustomerShell user={{ userId: 1, username: "林木" }} cartCount={2} onLogout={vi.fn()} />}>
            <Route path="/app/home" element={<p>首页内容</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByRole("navigation", { name: "商城主导航" })).toBeVisible();
    const mobile = screen.getByRole("navigation", { name: "移动端主导航" });
    expect(within(mobile).getAllByRole("link")).toHaveLength(5);
    expect(screen.getAllByText(/购物袋/)[0]).toHaveTextContent("2");
    expect(screen.getByText("首页内容")).toBeVisible();
  });
});
