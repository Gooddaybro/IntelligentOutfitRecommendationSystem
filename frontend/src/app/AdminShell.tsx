import { BarChart3, Boxes, ClipboardList, FolderTree, LayoutDashboard, LogOut, PackageSearch, ScrollText, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";
import type { CurrentUserResponse } from "../shared/api/types";
import { ADMIN_NAV_ITEMS } from "./navigation";

type AdminShellProps = { user: CurrentUserResponse; onLogout: () => void };

const icons: Record<string, LucideIcon> = {
  dashboard: LayoutDashboard,
  products: PackageSearch,
  categories: FolderTree,
  inventory: Boxes,
  orders: ClipboardList,
  users: Users,
  analytics: BarChart3,
  audit: ScrollText
};

export function AdminShell({ user, onLogout }: AdminShellProps) {
  return (
    <div className="admin-shell" data-testid="admin-shell">
      <aside className="admin-sidebar">
        <div className="admin-brand"><strong>水木管理台</strong><small>SHUIMU COMMERCE</small></div>
        <nav aria-label="管理后台导航">
          {ADMIN_NAV_ITEMS.map((item) => {
            const Icon = icons[item.key];
            return <NavLink key={item.key} end={item.to === "/admin"} to={item.to}><Icon size={18} /><span>{item.label}</span></NavLink>;
          })}
        </nav>
        <button className="admin-logout" type="button" onClick={onLogout}><LogOut size={17} />退出登录</button>
      </aside>
      <section className="admin-main">
        <header className="admin-topbar"><span>运营工作台</span><strong>{user.username}</strong></header>
        <main className="admin-content"><Outlet /></main>
      </section>
    </div>
  );
}
