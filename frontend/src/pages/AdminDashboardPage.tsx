import { ArrowUpRight, Boxes, ClipboardList, PackagePlus, TriangleAlert } from "lucide-react";

const metrics = [
  { label: "在售商品", icon: Boxes },
  { label: "待处理订单", icon: ClipboardList },
  { label: "库存预警", icon: TriangleAlert },
  { label: "本月成交", icon: ArrowUpRight }
];

export function AdminDashboardPage() {
  return (
    <section className="admin-dashboard">
      <header className="admin-page-heading"><div><p>COMMERCE OVERVIEW</p><h1>数据概览</h1><span>商品、订单与库存的统一运营入口</span></div><a href="/admin/products/new"><PackagePlus size={18} />新增商品</a></header>
      <div className="admin-metrics">
        {metrics.map(({ label, icon: Icon }) => <article key={label}><span><Icon size={20} /></span><p>{label}</p><strong>—</strong><small>待接入真实数据</small></article>)}
      </div>
      <div className="admin-dashboard-grid">
        <section><header><h2>成交趋势</h2><span>最近 30 天</span></header><div className="admin-chart-placeholder"><div /><div /><div /><div /><div /><p>后端统计接口接入后展示真实趋势</p></div></section>
        <section><header><h2>待办事项</h2></header><ul><li><TriangleAlert size={17} /><span><strong>库存预警</strong><small>等待真实库存阈值</small></span></li><li><ClipboardList size={17} /><span><strong>订单处理</strong><small>等待管理端订单接口</small></span></li></ul></section>
      </div>
    </section>
  );
}
