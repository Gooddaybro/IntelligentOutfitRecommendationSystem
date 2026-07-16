import { Heart, LockKeyhole, MapPin, Ruler, UserRound } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";

const items = [
  { to: "account", label: "个人资料", icon: UserRound },
  { to: "wardrobe", label: "衣橱画像", icon: Ruler },
  { to: "favorites", label: "我的收藏", icon: Heart },
  { to: "addresses", label: "收货地址", icon: MapPin },
  { to: "security", label: "账户安全", icon: LockKeyhole }
];

export function ProfileCenterPage() {
  return <main className="profile-center"><header className="page-intro"><div><p className="section-kicker">MY SHUIMU</p><h1>个人中心</h1><p>管理你的账户、衣橱信号和购物资料。</p></div></header><div className="profile-center-layout"><nav className="profile-subnav" aria-label="个人中心导航">{items.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} className={({ isActive }) => isActive ? "is-active" : undefined}><Icon size={18}/><span>{label}</span></NavLink>)}</nav><section className="profile-content"><Outlet/></section></div></main>;
}

