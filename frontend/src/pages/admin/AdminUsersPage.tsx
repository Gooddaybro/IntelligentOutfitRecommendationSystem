import { Search, ShieldCheck, UserRound } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import type { AdminUser, AdminUserStatus } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";

type UserStatusFilter = "ALL" | AdminUserStatus;

const T = {
  loadFailed: "\u7528\u6237\u6570\u636e\u52a0\u8f7d\u5931\u8d25",
  statusFailed: "\u7528\u6237\u72b6\u6001\u53d8\u66f4\u5931\u8d25",
  active: "\u6b63\u5e38",
  disabled: "\u5df2\u7981\u7528",
  title: "\u7528\u6237\u7ba1\u7406",
  subtitle: "\u4ec5\u5c55\u793a\u8d26\u53f7\u8fd0\u8425\u6240\u9700\u7684\u57fa\u7840\u8d44\u6599\u3001\u72b6\u6001\u548c\u8ba2\u5355\u6458\u8981\uff0c\u4e0d\u66b4\u9732\u5bc6\u7801\u5b57\u6bb5\u3002",
  allUsers: "\u5168\u90e8\u7528\u6237",
  activeAccounts: "\u6b63\u5e38\u8d26\u53f7",
  searchUser: "\u641c\u7d22\u7528\u6237",
  searchPlaceholder: "ID\u3001\u7528\u6237\u540d\u6216\u6635\u79f0",
  userStatus: "\u7528\u6237\u72b6\u6001",
  userStatusFilter: "\u7528\u6237\u72b6\u6001\u7b5b\u9009",
  allStatus: "\u5168\u90e8\u72b6\u6001",
  currentPrefix: "\u5f53\u524d",
  currentSuffix: "\u4e2a\u7528\u6237",
  empty: "\u6ca1\u6709\u7b26\u5408\u5f53\u524d\u6761\u4ef6\u7684\u7528\u6237",
  user: "\u7528\u6237",
  contact: "\u8054\u7cfb\u65b9\u5f0f",
  registeredAt: "\u6ce8\u518c\u65f6\u95f4",
  orders: "\u8ba2\u5355",
  paidAmount: "\u7d2f\u8ba1\u652f\u4ed8",
  status: "\u72b6\u6001",
  actions: "\u64cd\u4f5c",
  unboundPhone: "\u672a\u7ed1\u5b9a\u624b\u673a",
  unboundEmail: "\u672a\u7ed1\u5b9a\u90ae\u7bb1",
  view: "\u67e5\u770b",
  disable: "\u7981\u7528",
  enable: "\u542f\u7528",
  detailTitle: "\u7528\u6237\u8be6\u60c5",
  close: "\u5173\u95ed",
  userId: "\u7528\u6237 ID",
  username: "\u7528\u6237\u540d",
  nickname: "\u6635\u79f0",
  unset: "\u672a\u8bbe\u7f6e",
  orderCount: "\u8ba2\u5355\u6570\u91cf",
  orderUnit: "\u7b14",
  note: "\u8bf4\u660e",
  noteText: "\u8863\u6a71\u753b\u50cf\u5c5e\u4e8e\u7528\u6237\u4e2a\u4eba\u4e2d\u5fc3\u5b50\u9875\u9762\uff0c\u7ba1\u7406\u540e\u53f0\u5f53\u524d\u53ea\u8bfb\u5c55\u793a\u8d26\u53f7\u4e0e\u8ba2\u5355\u6458\u8981\u3002",
  confirmDisableUser: "\u786e\u8ba4\u7981\u7528\u7528\u6237",
  confirmEnableUser: "\u786e\u8ba4\u542f\u7528\u7528\u6237",
  confirmDisable: "\u786e\u8ba4\u7981\u7528",
  confirmEnable: "\u786e\u8ba4\u542f\u7528",
  cancel: "\u53d6\u6d88",
  submitting: "\u63d0\u4ea4\u4e2d\u2026",
  operating: "\u4f60\u6b63\u5728",
  userWord: "\u7528\u6237",
  effect: "\u3002\u8be5\u64cd\u4f5c\u4f1a\u5f71\u54cd\u7528\u6237\u767b\u5f55\u548c\u4e0b\u5355\u80fd\u529b\uff0c\u8bf7\u786e\u8ba4\u540e\u7ee7\u7eed\u3002"
} as const;

const money = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value)); }
function statusLabel(status: AdminUserStatus) { return status === "ACTIVE" ? T.active : T.disabled; }

export function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<UserStatusFilter>("ALL");
  const [selected, setSelected] = useState<AdminUser>();
  const [pendingStatusUser, setPendingStatusUser] = useState<AdminUser>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => { api.adminUsers().then(setUsers).catch((value) => setError(value instanceof Error ? value.message : T.loadFailed)); }, []);
  const visibleUsers = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return users.filter((user) => {
      const matchesKeyword = !normalizedKeyword || `${user.userId} ${user.username} ${user.nickname || ""}`.toLowerCase().includes(normalizedKeyword);
      const matchesStatus = statusFilter === "ALL" || user.status === statusFilter;
      return matchesKeyword && matchesStatus;
    });
  }, [keyword, statusFilter, users]);
  const targetStatus: AdminUserStatus | undefined = pendingStatusUser?.status === "ACTIVE" ? "DISABLED" : pendingStatusUser ? "ACTIVE" : undefined;
  const confirmTitle = targetStatus === "DISABLED" ? T.confirmDisableUser : T.confirmEnableUser;
  const confirmAction = targetStatus === "DISABLED" ? T.confirmDisable : T.confirmEnable;

  async function submitStatusChange() {
    if (!pendingStatusUser || !targetStatus) return;
    setBusy(true);
    setError("");
    try {
      const updated = await api.adminSetUserStatus(pendingStatusUser.userId, targetStatus);
      setUsers((items) => items.map((item) => item.userId === updated.userId ? updated : item));
      setSelected((current) => current?.userId === updated.userId ? updated : current);
      setPendingStatusUser(undefined);
    } catch (value) {
      setError(value instanceof Error ? value.message : T.statusFailed);
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-list-page admin-users-page">
    <header className="admin-page-heading"><div><p>USER OPERATIONS</p><h1>{T.title}</h1><span>{T.subtitle}</span></div></header>
    <div className="admin-inventory-summary" aria-label="user-summary"><article><UserRound size={20}/><div><strong>{users.length}</strong><span>{T.allUsers}</span></div></article><article><ShieldCheck size={20}/><div><strong>{users.filter((user) => user.status === "ACTIVE").length}</strong><span>{T.activeAccounts}</span></div></article></div>
    <div className="admin-filter-bar"><label className="admin-search"><Search size={17}/><input aria-label={T.searchUser} value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder={T.searchPlaceholder}/></label><label>{T.userStatus}<select aria-label={T.userStatusFilter} value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as UserStatusFilter)}><option value="ALL">{T.allStatus}</option><option value="ACTIVE">{T.active}</option><option value="DISABLED">{T.disabled}</option></select></label><span>{T.currentPrefix} {visibleUsers.length} {T.currentSuffix}</span></div>
    {error && <p className="admin-inline-error">{error}</p>}
    <AdminDataTable headers={[T.user, T.contact, T.registeredAt, T.orders, T.paidAmount, T.status, T.actions]} emptyText={T.empty} rows={visibleUsers.map((user) => ({ key: user.userId, cells: [<div className="admin-user-cell"><span>{user.nickname?.slice(0, 1) || user.username.slice(0, 1)}</span><div><strong>{user.username}</strong><small>ID {user.userId}{user.nickname ? ` ? ${user.nickname}` : ""}</small></div></div>, <span className="admin-contact-stack"><b>{user.phone || T.unboundPhone}</b><small>{user.email || T.unboundEmail}</small></span>, formatDate(user.registeredAt), `${user.orderCount} ${T.orderUnit}`, money.format(user.paidAmount), <AdminStatusBadge status={user.status} label={statusLabel(user.status)} />, <div className="admin-row-actions"><button type="button" onClick={() => setSelected(user)}>{T.view}</button><button type="button" onClick={() => setPendingStatusUser(user)}>{user.status === "ACTIVE" ? T.disable : T.enable}</button></div>] }))} />
    {selected && <div className="admin-drawer-backdrop" onClick={() => setSelected(undefined)}><aside className="admin-drawer" role="dialog" aria-modal="true" aria-labelledby="user-detail-title" onClick={(event) => event.stopPropagation()}><header><div><p>USER DETAIL</p><h2 id="user-detail-title">{T.detailTitle}</h2></div><button type="button" onClick={() => setSelected(undefined)}>{T.close}</button></header><dl className="admin-detail-grid"><div><dt>{T.userId}</dt><dd>{selected.userId}</dd></div><div><dt>{T.username}</dt><dd>{selected.username}</dd></div><div><dt>{T.nickname}</dt><dd>{selected.nickname || T.unset}</dd></div><div><dt>{T.userStatus}</dt><dd>{statusLabel(selected.status)}</dd></div><div><dt>{T.registeredAt}</dt><dd>{formatDate(selected.registeredAt)}</dd></div><div><dt>{T.orderCount}</dt><dd>{selected.orderCount} {T.orderUnit}</dd></div><div className="admin-detail-wide"><dt>{T.paidAmount}</dt><dd>{money.format(selected.paidAmount)}</dd></div><div className="admin-detail-wide"><dt>{T.note}</dt><dd>{T.noteText}</dd></div></dl></aside></div>}
    {pendingStatusUser && targetStatus && <div className="admin-dialog-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="user-status-title" className="admin-dialog"><h2 id="user-status-title">{confirmTitle}</h2><p>{T.operating}{targetStatus === "DISABLED" ? T.disable : T.enable}{T.userWord} <strong>{pendingStatusUser.username}</strong>{T.effect}</p><div><button type="button" onClick={() => setPendingStatusUser(undefined)} disabled={busy}>{T.cancel}</button><button className={targetStatus === "DISABLED" ? "admin-danger-button" : "admin-primary-button"} type="button" onClick={() => void submitStatusChange()} disabled={busy}>{busy ? T.submitting : confirmAction}</button></div></section></div>}
  </section>;
}
