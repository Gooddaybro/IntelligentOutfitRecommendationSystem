import { BarChart3, Flame, RefreshCw, ShoppingBag, TrendingUp } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { AdminAnalytics } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";

const T = {
  loadFailed: "\u7ecf\u8425\u5206\u6790\u6570\u636e\u52a0\u8f7d\u5931\u8d25",
  title: "\u7ecf\u8425\u5206\u6790",
  subtitle: "\u53ea\u5c55\u793a\u540e\u7aef\u8fd4\u56de\u7684\u7edf\u8ba1\u4e8b\u5b9e\uff0c\u4e0d\u5728\u524d\u7aef\u968f\u673a\u751f\u6210\u7ecf\u8425\u6570\u636e\u3002",
  loading: "\u6b63\u5728\u52a0\u8f7d\u7ecf\u8425\u6570\u636e\u2026",
  reload: "\u91cd\u65b0\u52a0\u8f7d",
  range: "\u7edf\u8ba1\u8303\u56f4",
  orderCount: "\u8ba2\u5355\u91cf",
  paidAmount: "\u6210\u4ea4\u989d",
  trend: "\u6210\u4ea4\u8d8b\u52bf",
  funnel: "\u8f6c\u5316\u6f0f\u6597",
  exposed: "\u66dd\u5149",
  clicked: "\u70b9\u51fb",
  cartAdded: "\u52a0\u8d2d",
  purchased: "\u6210\u4ea4",
  hotProducts: "\u70ed\u95e8\u5546\u54c1",
  categoryTrend: "\u5206\u7c7b\u9500\u552e",
  sales: "\u4ef6",
  emptyTrend: "\u5f53\u524d\u7edf\u8ba1\u8303\u56f4\u5185\u6682\u65e0\u8d8b\u52bf\u6570\u636e",
  emptyHot: "\u5f53\u524d\u7edf\u8ba1\u8303\u56f4\u5185\u6682\u65e0\u70ed\u95e8\u5546\u54c1"
} as const;

const money = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });
const numberFormatter = new Intl.NumberFormat("zh-CN");

export function AdminAnalyticsPage() {
  const [analytics, setAnalytics] = useState<AdminAnalytics>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      setAnalytics(await api.adminAnalytics());
    } catch (value) {
      setError(value instanceof Error ? value.message : T.loadFailed);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  const maxTrendAmount = useMemo(() => Math.max(1, ...(analytics?.trend.map((item) => item.paidAmount) || [])), [analytics]);
  const funnelItems = analytics ? [
    { label: T.exposed, value: analytics.funnel.exposed },
    { label: T.clicked, value: analytics.funnel.clicked },
    { label: T.cartAdded, value: analytics.funnel.cartAdded },
    { label: T.purchased, value: analytics.funnel.purchased }
  ] : [];
  const maxFunnelValue = Math.max(1, ...funnelItems.map((item) => item.value));
  const maxCategorySales = Math.max(1, ...(analytics?.categoryTrend.map((item) => item.sales) || []));

  return <section className="admin-list-page admin-analytics-page">
    <header className="admin-page-heading"><div><p>BUSINESS ANALYTICS</p><h1>{T.title}</h1><span>{T.subtitle}</span></div></header>
    {loading && <div className="admin-loading">{T.loading}</div>}
    {error && <div className="admin-error"><span>{error}</span><button type="button" onClick={() => void load()}><RefreshCw size={16}/>{T.reload}</button></div>}
    {analytics && <>
      <div className="admin-analytics-kpis">
        <article><BarChart3 size={20}/><span>{T.range}</span><strong>{analytics.rangeLabel}</strong></article>
        <article><ShoppingBag size={20}/><span>{T.orderCount}</span><strong>{numberFormatter.format(analytics.orderCount)}</strong></article>
        <article><TrendingUp size={20}/><span>{T.paidAmount}</span><strong>{money.format(analytics.paidAmount)}</strong></article>
      </div>
      <div className="admin-analytics-grid">
        <section><header><h2>{T.trend}</h2><span>{analytics.rangeLabel}</span></header>{analytics.trend.length ? <div className="admin-trend-chart" aria-label={T.trend}>{analytics.trend.map((item) => <div key={item.label}><span style={{ height: `${Math.max(8, item.paidAmount / maxTrendAmount * 100)}%` }} title={`${item.label} ${money.format(item.paidAmount)}`} /><small>{item.label}<br/>{numberFormatter.format(item.orderCount)}</small></div>)}</div> : <div className="admin-empty-state">{T.emptyTrend}</div>}</section>
        <section><header><h2>{T.funnel}</h2><span>{analytics.rangeLabel}</span></header><p className="admin-funnel-definition">{analytics.funnel.definition}</p><div className="admin-funnel-list">{funnelItems.map((item) => <div key={item.label}><span>{item.label}</span><strong>{numberFormatter.format(item.value)}</strong><i style={{ width: `${Math.max(6, item.value / maxFunnelValue * 100)}%` }} /></div>)}</div></section>
        <section><header><h2><Flame size={18}/>{T.hotProducts}</h2><span>{T.paidAmount}</span></header>{analytics.hotProducts.length ? <ol className="admin-ranked-list">{analytics.hotProducts.map((item, index) => <li key={item.spuId}><b>{index + 1}</b><span>{item.name}<small>{item.sales} {T.sales}</small></span><strong>{money.format(item.paidAmount)}</strong></li>)}</ol> : <div className="admin-empty-state">{T.emptyHot}</div>}</section>
        <section><header><h2>{T.categoryTrend}</h2><span>{T.sales}</span></header><div className="admin-category-bars">{analytics.categoryTrend.map((item) => <div key={item.categoryName}><span>{item.categoryName}</span><strong>{item.sales}</strong><i style={{ width: `${Math.max(8, item.sales / maxCategorySales * 100)}%` }} /></div>)}</div></section>
      </div>
    </>}
  </section>;
}
