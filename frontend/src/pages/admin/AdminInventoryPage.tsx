import { Boxes, Search, TriangleAlert } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import type { AdminSku } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";

type StockFilter = "ALL" | "LOW" | "NORMAL";

function isLowStock(sku: AdminSku) {
  return sku.availableStock <= sku.lowStockThreshold;
}

function formatAdjustment(sku: AdminSku) {
  if (!sku.lastAdjustment) return "暂无记录";
  const { beforeStock, afterStock, reason } = sku.lastAdjustment;
  return `${beforeStock} → ${afterStock} · ${reason}`;
}

export function AdminInventoryPage() {
  const [skus, setSkus] = useState<AdminSku[]>([]);
  const [keyword, setKeyword] = useState("");
  const [stockFilter, setStockFilter] = useState<StockFilter>("ALL");
  const [pending, setPending] = useState<AdminSku>();
  const [targetStock, setTargetStock] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api.adminInventory()
      .then(setSkus)
      .catch((value) => setError(value instanceof Error ? value.message : "库存数据加载失败"));
  }, []);

  const lowStockCount = useMemo(() => skus.filter(isLowStock).length, [skus]);
  const visibleSkus = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return skus.filter((sku) => {
      const matchesKeyword = !normalizedKeyword || `${sku.productName} ${sku.skuCode}`.toLowerCase().includes(normalizedKeyword);
      const lowStock = isLowStock(sku);
      const matchesStock = stockFilter === "ALL" || (stockFilter === "LOW" ? lowStock : !lowStock);
      return matchesKeyword && matchesStock;
    });
  }, [keyword, skus, stockFilter]);

  const parsedTargetStock = Number(targetStock);
  const adjustmentValid = targetStock !== "" && Number.isInteger(parsedTargetStock) && parsedTargetStock >= 0 && reason.trim().length > 0;

  function openAdjustment(sku: AdminSku) {
    setPending(sku);
    setTargetStock(String(sku.availableStock));
    setReason("");
    setError("");
  }

  function closeAdjustment() {
    if (busy) return;
    setPending(undefined);
    setTargetStock("");
    setReason("");
  }

  async function submitAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!pending || !adjustmentValid) return;
    setBusy(true);
    setError("");
    try {
      const updated = await api.adminAdjustInventory(pending.skuId, parsedTargetStock, reason.trim());
      setSkus((items) => items.map((item) => item.skuId === updated.skuId ? updated : item));
      setPending(undefined);
      setTargetStock("");
      setReason("");
    } catch (value) {
      setError(value instanceof Error ? value.message : "库存调整失败");
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-list-page admin-inventory-page">
    <header className="admin-page-heading">
      <div><p>SKU &amp; INVENTORY</p><h1>SKU / 库存</h1><span>核对可售库存、预警阈值并留存每次人工调整原因</span></div>
    </header>

    <div className="admin-inventory-summary" aria-label="库存摘要">
      <article><Boxes size={20}/><div><strong>{skus.length}</strong><span>全部 SKU</span></div></article>
      <article className={lowStockCount ? "is-warning" : ""}><TriangleAlert size={20}/><div><strong>{lowStockCount}</strong><span>库存预警</span></div></article>
    </div>

    <div className="admin-filter-bar">
      <label className="admin-search"><Search size={17}/><input aria-label="搜索库存" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="商品名或 SKU 编码"/></label>
      <label>库存状态<select aria-label="库存状态筛选" value={stockFilter} onChange={(event) => setStockFilter(event.target.value as StockFilter)}><option value="ALL">全部库存</option><option value="LOW">库存预警</option><option value="NORMAL">库存正常</option></select></label>
      <span>当前 {visibleSkus.length} 个 SKU</span>
    </div>

    {error && <p className="admin-inline-error">{error}</p>}
    <AdminDataTable
      headers={["SKU 编码", "商品", "规格", "售价", "可售库存", "预警阈值", "状态", "最近调整", "操作"]}
      emptyText="没有符合当前条件的 SKU"
      rows={visibleSkus.map((sku) => {
        const lowStock = isLowStock(sku);
        return { key: sku.skuId, cells: [
          <code>{sku.skuCode}</code>,
          <strong>{sku.productName}</strong>,
          <span>{[sku.color, sku.size].filter(Boolean).join(" / ") || "默认规格"}</span>,
          `¥${sku.salePrice.toLocaleString("zh-CN")}`,
          <strong className={lowStock ? "admin-stock-warning" : ""}>{sku.availableStock}</strong>,
          sku.lowStockThreshold,
          sku.status === "INACTIVE"
            ? <AdminStatusBadge status="DISABLED" label="已停用"/>
            : <AdminStatusBadge status={lowStock ? "OFF_SHELF" : "ACTIVE"} label={lowStock ? "库存预警" : "库存正常"}/>,
          <span className="admin-adjustment-summary">{formatAdjustment(sku)}</span>,
          <div className="admin-row-actions"><button type="button" onClick={() => openAdjustment(sku)}>调整库存</button></div>
        ] };
      })}
    />

    {pending && <div className="admin-dialog-backdrop">
      <section role="dialog" aria-modal="true" aria-labelledby="inventory-adjustment-title" className="admin-dialog">
        <h2 id="inventory-adjustment-title">调整库存</h2>
        <p><strong>{pending.productName}</strong><br/><code>{pending.skuCode}</code> · 当前可售库存 {pending.availableStock}</p>
        <form className="admin-adjustment-form" onSubmit={(event) => void submitAdjustment(event)}>
          <label>目标库存<input aria-label="目标库存" type="number" min="0" step="1" value={targetStock} onChange={(event) => setTargetStock(event.target.value)}/></label>
          <label>调整原因<textarea aria-label="调整原因" rows={3} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="例如：到货入库、盘点修正"/></label>
          <small>系统会记录调整前后数量、原因、操作人和时间。</small>
          <footer><button type="button" onClick={closeAdjustment}>取消</button><button className="admin-primary-button" type="submit" disabled={!adjustmentValid || busy}>{busy ? "提交中…" : "确认调整"}</button></footer>
        </form>
      </section>
    </div>}
  </section>;
}
