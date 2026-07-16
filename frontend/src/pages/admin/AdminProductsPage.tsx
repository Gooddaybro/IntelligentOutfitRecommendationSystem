import { PackagePlus, Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import { filterAdminProducts } from "../../features/admin/adminFilters";
import { api } from "../../shared/api/client";
import type { AdminProduct } from "../../shared/api/adminTypes";

const statusLabels: Record<AdminProduct["status"], string> = { DRAFT: "草稿", ON_SALE: "在售", OFF_SHELF: "已下架", DELETED: "已删除" };

export function AdminProductsPage() {
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("ALL");
  const [status, setStatus] = useState<AdminProduct["status"] | "ALL">("ALL");
  const [pending, setPending] = useState<AdminProduct>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => { api.adminProducts().then(setProducts).catch((value) => setError(value instanceof Error ? value.message : "商品加载失败")); }, []);
  const categories = useMemo(() => Array.from(new Set(products.map((item) => item.categoryName))), [products]);
  const visibleProducts = useMemo(() => filterAdminProducts(products, { keyword, category, status }), [category, keyword, products, status]);

  async function confirmStatus() {
    if (!pending) return;
    setBusy(true);
    setError("");
    const nextStatus = pending.status === "ON_SALE" ? "OFF_SHELF" : "ON_SALE";
    try {
      const updated = await api.adminSetProductStatus(pending.spuId, nextStatus);
      setProducts((items) => items.map((item) => item.spuId === updated.spuId ? updated : item));
      setPending(undefined);
    } catch (value) {
      setError(value instanceof Error ? value.message : "商品状态更新失败");
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-list-page">
    <header className="admin-page-heading"><div><p>PRODUCT OPERATIONS</p><h1>商品管理</h1><span>维护 SPU 基础信息、上下架状态与商品入口</span></div><Link to="/admin/products/new"><PackagePlus size={18}/>新增商品</Link></header>
    <div className="admin-filter-bar">
      <label className="admin-search"><Search size={17}/><input aria-label="搜索商品" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="商品名或 SPU 编码"/></label>
      <label>分类<select aria-label="商品分类筛选" value={category} onChange={(event) => setCategory(event.target.value)}><option value="ALL">全部分类</option>{categories.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label>状态<select aria-label="商品状态筛选" value={status} onChange={(event) => setStatus(event.target.value as typeof status)}><option value="ALL">全部状态</option><option value="ON_SALE">在售</option><option value="OFF_SHELF">已下架</option><option value="DRAFT">草稿</option></select></label>
      <span>共 {visibleProducts.length} 件商品</span>
    </div>
    {error && <p className="admin-inline-error">{error}</p>}
    <AdminDataTable
      headers={["商品", "SPU 编码", "分类", "价格", "SKU", "总库存", "状态", "操作"]}
      emptyText="没有符合当前条件的商品"
      rows={visibleProducts.map((product) => ({ key: product.spuId, cells: [
        <div className="admin-product-cell">{product.mainImageUrl ? <img src={product.mainImageUrl} alt=""/> : <span/>}<strong>{product.name}</strong></div>,
        <code>{product.spuCode}</code>,
        product.categoryName,
        product.minPrice === product.maxPrice ? `¥${product.minPrice}` : `¥${product.minPrice}–${product.maxPrice}`,
        product.skuCount,
        product.totalStock,
        <AdminStatusBadge status={product.status} label={statusLabels[product.status]}/>,
        <div className="admin-row-actions"><Link to={`/admin/products/${product.spuId}/edit`}>编辑</Link>{product.status !== "DRAFT" && <button type="button" onClick={() => setPending(product)}>{product.status === "ON_SALE" ? "下架" : "上架"}</button>}</div>
      ] }))}
    />
    {pending && <div className="admin-dialog-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="product-status-title" className="admin-dialog"><h2 id="product-status-title">确认{pending.status === "ON_SALE" ? "下架" : "上架"}商品</h2><p>{pending.status === "ON_SALE" ? "下架后用户将无法购买这件商品，已有订单不会受到影响。" : "上架后商品将重新出现在用户商城，请确认价格与库存已核对。"}</p><strong>{pending.name}</strong><div><button type="button" onClick={() => setPending(undefined)}>取消</button><button className="admin-danger-button" type="button" disabled={busy} onClick={() => void confirmStatus()}>确认{pending.status === "ON_SALE" ? "下架" : "上架"}</button></div></section></div>}
  </section>;
}
