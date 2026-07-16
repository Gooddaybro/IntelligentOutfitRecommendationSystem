import {
  Bot,
  Heart,
  Home,
  LogOut,
  PackageSearch,
  ReceiptText,
  Search,
  ShoppingBag,
  UserRound
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import type { CurrentUserResponse } from "../shared/api/types";
import { APP_NAV_ITEMS } from "./navigation";

type CustomerShellProps = {
  user: CurrentUserResponse;
  cartCount: number;
  onLogout: () => void;
  isDemoMode?: boolean;
};

const icons: Record<string, LucideIcon> = {
  home: Home,
  ai: Bot,
  products: PackageSearch,
  cart: ShoppingBag,
  orders: ReceiptText,
  profile: UserRound
};

const mobileKeys = new Set(["home", "ai", "products", "cart", "profile"]);

function NavItem({ item, cartCount }: { item: (typeof APP_NAV_ITEMS)[number]; cartCount: number }) {
  const Icon = icons[item.key];
  const label = item.key === "cart" && cartCount > 0 ? `${item.label} ${cartCount}` : item.label;
  return (
    <NavLink to={item.to} className={({ isActive }) => isActive ? "is-active" : undefined}>
      <Icon size={19} />
      <span>{label}</span>
    </NavLink>
  );
}

export function CustomerShell({ user, cartCount, onLogout, isDemoMode = false }: CustomerShellProps) {
  return (
    <div className="shuimu-shell" data-testid="customer-shell">
      <aside className="shuimu-sidebar">
        <NavLink className="shuimu-brand" to="/app/home" aria-label="水木穿搭首页">
          <span className="shuimu-brand__mark"><Heart size={19} /></span>
          <span><strong>水木</strong><small>AI 穿搭生活</small></span>
        </NavLink>
        {isDemoMode && <span className="demo-mode-badge">前端演示数据</span>}
        <nav className="shuimu-nav" aria-label="商城主导航">
          {APP_NAV_ITEMS.map((item, index) => (
            <div key={item.key} className={index === 3 || index === 5 ? "shuimu-nav__group-start" : undefined}>
              <NavItem item={item} cartCount={cartCount} />
            </div>
          ))}
        </nav>
        <div className="shuimu-sidebar__user">
          <span className="shuimu-avatar">{user.username.slice(0, 1).toUpperCase()}</span>
          <span><strong>{user.username}</strong><small>偏好已同步</small></span>
          <button type="button" onClick={onLogout} aria-label="退出登录"><LogOut size={17} /></button>
        </div>
      </aside>

      <section className="shuimu-main">
        <header className="shuimu-topbar">
          <label className="shuimu-search">
            <Search size={17} />
            <input aria-label="全站搜索" placeholder="搜索商品、风格或场景" />
          </label>
          <div className="shuimu-topbar__user">
            <span>你好，{user.username}</span>
            <span className="shuimu-avatar">{user.username.slice(0, 1).toUpperCase()}</span>
          </div>
        </header>
        <div className="shuimu-content"><Outlet /></div>
      </section>

      <nav className="shuimu-mobile-nav" aria-label="移动端主导航">
        {APP_NAV_ITEMS.filter((item) => mobileKeys.has(item.key)).map((item) => (
          <NavItem key={item.key} item={item} cartCount={cartCount} />
        ))}
      </nav>
    </div>
  );
}
