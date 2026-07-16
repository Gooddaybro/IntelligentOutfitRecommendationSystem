import { FolderPlus } from "lucide-react";
import { useEffect, useState } from "react";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import { api } from "../../shared/api/client";
import type { AdminCategory } from "../../shared/api/adminTypes";

export function AdminCategoriesPage() {
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [pending, setPending] = useState<AdminCategory>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => { api.adminCategories().then(setCategories).catch((value) => setError(value instanceof Error ? value.message : "分类加载失败")); }, []);

  async function toggle() {
    if (!pending) return;
    setBusy(true);
    setError("");
    try {
      const updated = await api.adminUpdateCategory({ ...pending, enabled: !pending.enabled });
      setCategories((items) => items.map((item) => item.id === updated.id ? updated : item));
      setPending(undefined);
    } catch (value) {
      setError(value instanceof Error ? value.message : "分类状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-list-page">
    <header className="admin-page-heading"><div><p>CATALOG STRUCTURE</p><h1>分类管理</h1><span>首期维护一级与二级分类、排序及启停状态</span></div><button className="admin-heading-action" type="button"><FolderPlus size={18}/>新增分类</button></header>
    <div className="admin-callout">停用分类不会删除历史商品，但会影响商城分类入口；操作前请核对关联商品数量。</div>
    {error && <p className="admin-inline-error">{error}</p>}
    <AdminDataTable
      headers={["分类名称", "层级", "父分类", "排序", "关联商品", "状态", "操作"]}
      emptyText="暂无分类数据"
      rows={[...categories].sort((a, b) => a.sortOrder - b.sortOrder).map((category) => ({ key: category.id, cells: [
        <strong>{category.level === 2 ? `└ ${category.name}` : category.name}</strong>,
        category.level === 1 ? "一级分类" : "二级分类",
        category.parentId ? categories.find((item) => item.id === category.parentId)?.name || "—" : "—",
        category.sortOrder,
        `${category.productCount} 件`,
        <AdminStatusBadge status={category.enabled ? "ACTIVE" : "DISABLED"} label={category.enabled ? "已启用" : "已停用"}/>,
        <div className="admin-row-actions"><button type="button">编辑</button><button type="button" onClick={() => setPending(category)}>{category.enabled ? "停用" : "启用"}</button></div>
      ] }))}
    />
    {pending && <div className="admin-dialog-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="category-status-title" className="admin-dialog"><h2 id="category-status-title">确认{pending.enabled ? "停用" : "启用"}分类</h2><p>分类“{pending.name}”当前关联 {pending.productCount} 件商品。{pending.enabled ? "停用后商城分类入口将不再展示该分类。" : "启用后该分类将恢复可用。"}</p><div><button type="button" onClick={() => setPending(undefined)}>取消</button><button className={pending.enabled ? "admin-danger-button" : "admin-primary-button"} type="button" disabled={busy} onClick={() => void toggle()}>确认{pending.enabled ? "停用" : "启用"}</button></div></section></div>}
  </section>;
}
