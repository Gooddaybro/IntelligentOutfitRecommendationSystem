import { CheckCircle2, MapPin, Package, RotateCcw, Truck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../shared/api/client";
import type { OrderResponse } from "../shared/api/types";

const labels: Record<string, string> = { PENDING_PAYMENT: "待付款", UNPAID: "待付款", PAID: "已付款", SHIPPED: "运输中", COMPLETED: "已完成", CANCELLED: "已取消", CLOSED: "已关闭" };

export function OrderDetailPage() {
  const { orderNo = "" } = useParams();
  const [order, setOrder] = useState<OrderResponse>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => { api.order(orderNo).then(setOrder).catch((value) => setError(value instanceof Error ? value.message : "订单加载失败")); }, [orderNo]);
  async function act(action: "cancel" | "confirm") { setBusy(true); try { setOrder(action === "cancel" ? await api.cancelOrder(orderNo) : await api.confirmReceipt(orderNo)); } finally { setBusy(false); } }
  if (error) return <div className="empty-note"><p>{error}</p><Link to="/app/orders">返回订单列表</Link></div>;
  if (!order) return <div className="empty-note"><p>正在加载订单详情…</p></div>;
  const pending = ["PENDING_PAYMENT", "UNPAID"].includes(order.status);
  return <main className="order-detail-page">
    <header className="page-intro"><div><p className="section-kicker">ORDER DETAIL</p><h1>订单 {order.orderNo}</h1><p>创建于 {new Date(order.createdAt).toLocaleString("zh-CN")}</p></div><span className="order-status">{labels[order.status] || order.status}</span></header>
    <section className="order-progress"><div className="is-done"><CheckCircle2/>提交订单</div><div className={!pending && order.status !== "CANCELLED" ? "is-done" : ""}><Package/>完成支付</div><div className={["SHIPPED", "COMPLETED"].includes(order.status) ? "is-done" : ""}><Truck/>商品发出</div><div className={order.status === "COMPLETED" ? "is-done" : ""}><CheckCircle2/>确认收货</div></section>
    <div className="order-detail-grid"><div>
      <section className="checkout-card"><h2><Package size={20}/>商品明细</h2>{order.items.map((item) => <article className="checkout-item" key={item.skuId}><span><strong>{item.productName}</strong><small>{item.color || "默认颜色"} · {item.size || "默认尺码"} × {item.quantity}</small></span><strong>¥{item.lineAmount.toFixed(2)}</strong></article>)}</section>
      {order.address && <section className="checkout-card"><h2><MapPin size={20}/>收货信息</h2><p>{order.address.recipientName}　{order.address.phone}</p><p>{order.address.province}{order.address.city}{order.address.district}{order.address.detail}</p></section>}
      <section className="checkout-card"><h2><Truck size={20}/>物流信息</h2>{order.shipment ? <><p>{order.shipment.carrier}　{order.shipment.trackingNo}</p><p>{order.shipment.latestEvent}</p></> : <p>{order.status === "PAID" ? "商家正在准备商品" : "暂无物流信息"}</p>}</section>
    </div><aside className="order-actions"><p>订单金额</p><strong>¥{order.totalAmount.toFixed(2)}</strong>{pending && <Link className="primary-button" to={`/app/payments/${order.orderNo}`}>继续支付</Link>}{pending && <button disabled={busy} onClick={() => void act("cancel")}>取消订单</button>}{order.status === "SHIPPED" && <button className="primary-button" disabled={busy} onClick={() => void act("confirm")}>确认收货</button>}<button disabled={!['PAID','SHIPPED','COMPLETED'].includes(order.status)}><RotateCcw size={16}/>申请售后</button><Link to="/app/orders">返回订单列表</Link></aside></div>
  </main>;
}
