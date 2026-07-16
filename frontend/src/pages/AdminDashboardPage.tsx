import { ArrowUpRight, Boxes, ClipboardList, PackagePlus, RefreshCw, RotateCcw, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../shared/api/client";
import type { AdminOverview } from "../shared/api/adminTypes";

const money = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });

export function AdminDashboardPage() {
  const [overview, setOverview] = useState<AdminOverview>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError("");
    try {
      setOverview(await api.adminOverview());
    } catch (value) {
      setError(value instanceof Error ? value.message : "概览数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  const metrics = overview ? [
    { label: "在售商品", value: String(overview.onSaleProducts), note: "当前可售 SPU", icon: Boxes },
    { label: "SKU 总数", value: String(overview.skuCount), note: "全部规格", icon: ClipboardList },
    { label: "库存预警", value: String(overview.lowStockCount), note: "低于预警阈值", icon: TriangleAlert },
    { label: "待发货订单", value: String(overview.pendingShipmentOrders), note: "需要运营处理", icon: ClipboardList },
    { label: "售后处理中", value: String(overview.afterSaleOrders), note: "需要跟进", icon: RotateCcw },
    { label: "支付金额", value: money.format(overview.paidAmount), note: overview.rangeLabel, icon: ArrowUpRight }
  ] : [];
  const maxTrendAmount = Math.max(1, ...(overview?.trend.map((item) => item.amount) || []));

  return (
    <section className="admin-dashboard">
      <header className="admin-page-heading"><div><p>COMMERCE OVERVIEW</p><h1>数据概览</h1><span>商品、订单与库存的统一运营入口</span></div><a href="/admin/products/new"><PackagePlus size={18} />新增商品</a></header>

      {loading && <div className="admin-loading">正在汇总运营数据…</div>}
      {error && <div className="admin-error"><span>{error}</span><button type="button" onClick={() => void load()}><RefreshCw size={16} />重新加载</button></div>}

      {overview && <>
        <div className="admin-metrics">
          {metrics.map(({ label, value, note, icon: Icon }) => <article key={label}><span><Icon size={20} /></span><p>{label}</p><strong>{value}</strong><small>{note}</small></article>)}
        </div>
        <div className="admin-dashboard-grid">
          <section><header><h2>成交趋势</h2><span>{overview.rangeLabel}</span></header>{overview.trend.length ? <div className="admin-trend-chart" aria-label="成交趋势图">{overview.trend.map((item) => <div key={item.label}><span style={{ height: `${Math.max(8, item.amount / maxTrendAmount * 100)}%` }} title={`${item.label} ${money.format(item.amount)}`} /><small>{item.label}</small></div>)}</div> : <div className="admin-empty-state">当前统计范围内暂无成交趋势</div>}</section>
          <section><header><h2>运营待办</h2><span>{overview.rangeLabel}</span></header><ul><li><TriangleAlert size={17} /><span><strong>{overview.lowStockCount} 个 SKU 库存预警</strong><small>进入库存管理核对并补货</small></span></li><li><ClipboardList size={17} /><span><strong>{overview.pendingShipmentOrders} 笔订单等待发货</strong><small>进入订单管理填写物流信息</small></span></li><li><RotateCcw size={17} /><span><strong>{overview.afterSaleOrders} 笔售后正在处理</strong><small>及时跟进用户申请</small></span></li></ul></section>
          <section className="admin-hot-products"><header><h2>热门商品</h2><span>按成交件数</span></header>{overview.hotProducts.length ? <ol>{overview.hotProducts.map((item, index) => <li key={item.spuId}><b>{index + 1}</b><span>{item.name}</span><strong>{item.sales} 件</strong></li>)}</ol> : <div className="admin-empty-state">当前统计范围内暂无热门商品</div>}</section>
        </div>
      </>}
    </section>
  );
}
