import { RefreshCw, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import type { AdminAuditLog } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";

const T = {
  loadFailed: "\u64cd\u4f5c\u65e5\u5fd7\u52a0\u8f7d\u5931\u8d25",
  title: "\u64cd\u4f5c\u65e5\u5fd7",
  subtitle: "\u5ba1\u8ba1\u65e5\u5fd7\u4ec5\u505a\u67e5\u770b\uff0c\u4e0d\u63d0\u4f9b\u7f16\u8f91\u6216\u5220\u9664\u5165\u53e3\u3002",
  loading: "\u6b63\u5728\u52a0\u8f7d\u64cd\u4f5c\u65e5\u5fd7\u2026",
  reload: "\u91cd\u65b0\u52a0\u8f7d",
  search: "\u641c\u7d22\u65e5\u5fd7",
  searchPlaceholder: "\u7ba1\u7406\u5458\u3001\u52a8\u4f5c\u6216\u5bf9\u8c61",
  currentPrefix: "\u5f53\u524d",
  currentSuffix: "\u6761\u65e5\u5fd7",
  operator: "\u7ba1\u7406\u5458",
  action: "\u52a8\u4f5c",
  target: "\u5bf9\u8c61",
  result: "\u7ed3\u679c",
  summary: "\u6458\u8981",
  time: "\u65f6\u95f4",
  success: "\u6210\u529f",
  failed: "\u5931\u8d25",
  empty: "\u6682\u65e0\u7b26\u5408\u6761\u4ef6\u7684\u64cd\u4f5c\u65e5\u5fd7"
} as const;

function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }

export function AdminAuditLogsPage() {
  const [logs, setLogs] = useState<AdminAuditLog[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try { setLogs(await api.adminAuditLogs()); }
    catch (value) { setError(value instanceof Error ? value.message : T.loadFailed); }
    finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, []);

  const visibleLogs = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return logs.filter((log) => !normalizedKeyword || `${log.operator} ${log.action} ${log.targetType} ${log.targetId} ${log.summary}`.toLowerCase().includes(normalizedKeyword));
  }, [keyword, logs]);

  return <section className="admin-list-page admin-audit-page">
    <header className="admin-page-heading"><div><p>AUDIT LOGS</p><h1>{T.title}</h1><span>{T.subtitle}</span></div></header>
    <div className="admin-filter-bar"><label className="admin-search"><Search size={17}/><input aria-label={T.search} value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder={T.searchPlaceholder}/></label><span>{T.currentPrefix} {visibleLogs.length} {T.currentSuffix}</span></div>
    {loading && <div className="admin-loading">{T.loading}</div>}
    {error && <div className="admin-error"><span>{error}</span><button type="button" onClick={() => void load()}><RefreshCw size={16}/>{T.reload}</button></div>}
    {!loading && <AdminDataTable headers={[T.operator, T.action, T.target, T.result, T.summary, T.time]} emptyText={T.empty} rows={visibleLogs.map((log) => ({ key: log.id, cells: [<strong>{log.operator}</strong>, <code>{log.action}</code>, `${log.targetType} / ${log.targetId}`, <AdminStatusBadge status={log.result} label={log.result === "SUCCESS" ? T.success : T.failed} />, log.summary, formatDate(log.createdAt)] }))} />}
  </section>;
}
