import { ClipboardList, Search, Truck } from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { AdminDataTable } from "../../features/admin/AdminDataTable";
import { AdminStatusBadge } from "../../features/admin/AdminStatusBadge";
import type { AdminOrder } from "../../shared/api/adminTypes";
import { api } from "../../shared/api/client";

type OrderStatusFilter = "ALL" | "UNPAID" | "PAID" | "SHIPPED" | "COMPLETED" | "CANCELLED";

const T = {
  loadFailed: "\u8ba2\u5355\u6570\u636e\u52a0\u8f7d\u5931\u8d25",
  shipFailed: "\u8ba2\u5355\u53d1\u8d27\u5931\u8d25",
  unpaid: "\u5f85\u4ed8\u6b3e",
  pendingShipment: "\u5f85\u53d1\u8d27",
  shipped: "\u5df2\u53d1\u8d27",
  completed: "\u5df2\u5b8c\u6210",
  cancelled: "\u5df2\u53d6\u6d88",
  title: "\u8ba2\u5355\u7ba1\u7406",
  subtitle: "\u6309\u8ba2\u5355\u72b6\u6001\u8ddf\u8fdb\u53d1\u8d27\u3001\u7269\u6d41\u548c\u652f\u4ed8\u6458\u8981\uff0c\u6240\u6709\u72b6\u6001\u4ee5\u63a5\u53e3\u8fd4\u56de\u4e3a\u51c6\u3002",
  allOrders: "\u5168\u90e8\u8ba2\u5355",
  searchOrder: "\u641c\u7d22\u8ba2\u5355",
  searchPlaceholder: "\u8ba2\u5355\u53f7\u6216\u7528\u6237\u540d",
  statusFilter: "\u8ba2\u5355\u72b6\u6001\u7b5b\u9009",
  status: "\u8ba2\u5355\u72b6\u6001",
  currentPrefix: "\u5f53\u524d",
  currentSuffix: "\u7b14\u8ba2\u5355",
  empty: "\u6ca1\u6709\u7b26\u5408\u5f53\u524d\u6761\u4ef6\u7684\u8ba2\u5355",
  orderNo: "\u8ba2\u5355\u53f7",
  user: "\u7528\u6237",
  payment: "\u652f\u4ed8",
  items: "\u5546\u54c1",
  amount: "\u91d1\u989d",
  createdAt: "\u4e0b\u5355\u65f6\u95f4",
  actions: "\u64cd\u4f5c",
  paid: "\u5df2\u652f\u4ed8",
  notPaid: "\u672a\u652f\u4ed8",
  pieces: "\u4ef6",
  view: "\u67e5\u770b",
  ship: "\u53d1\u8d27",
  detailTitle: "\u8ba2\u5355\u8be6\u60c5",
  close: "\u5173\u95ed",
  itemCount: "\u5546\u54c1\u6570\u91cf",
  address: "\u6536\u8d27\u5730\u5740",
  noAddress: "\u63a5\u53e3\u672a\u8fd4\u56de\u5730\u5740\u6458\u8981",
  shipment: "\u7269\u6d41\u4fe1\u606f",
  noShipment: "\u6682\u672a\u53d1\u8d27",
  shipDialog: "\u8ba2\u5355\u53d1\u8d27",
  shipTip: "\u8bf7\u586b\u5199\u771f\u5b9e\u627f\u8fd0\u5546\u548c\u8fd0\u5355\u53f7\uff0c\u63d0\u4ea4\u540e\u4ee5\u524d\u7aef\u6536\u5230\u7684\u63a5\u53e3\u7ed3\u679c\u5237\u65b0\u8ba2\u5355\u884c\u3002",
  carrier: "\u627f\u8fd0\u5546",
  carrierPlaceholder: "\u4f8b\u5982\uff1a\u987a\u4e30\u901f\u8fd0",
  trackingNo: "\u8fd0\u5355\u53f7",
  trackingPlaceholder: "\u4f8b\u5982\uff1aSF123456789",
  cancel: "\u53d6\u6d88",
  submitting: "\u63d0\u4ea4\u4e2d\u2026",
  confirmShip: "\u786e\u8ba4\u53d1\u8d27"
} as const;

const money = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" });
const statusLabels: Record<string, string> = { UNPAID: T.unpaid, PENDING_PAYMENT: T.unpaid, PAID: T.pendingShipment, SHIPPED: T.shipped, COMPLETED: T.completed, CANCELLED: T.cancelled };

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function canShip(order: AdminOrder) {
  return order.status === "PAID" && order.availableActions.includes("SHIP");
}

function renderOrderStatus(order: AdminOrder) {
  const badgeStatus = order.status === "UNPAID" || order.status === "PENDING_PAYMENT" ? "DRAFT" : order.status;
  return <AdminStatusBadge status={badgeStatus} label={statusLabels[order.status] || order.status} />;
}

export function AdminOrdersPage() {
  const [orders, setOrders] = useState<AdminOrder[]>([]);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<OrderStatusFilter>("ALL");
  const [selected, setSelected] = useState<AdminOrder>();
  const [shippingOrder, setShippingOrder] = useState<AdminOrder>();
  const [carrier, setCarrier] = useState("");
  const [trackingNo, setTrackingNo] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api.adminOrders().then(setOrders).catch((value) => setError(value instanceof Error ? value.message : T.loadFailed));
  }, []);

  const visibleOrders = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return orders.filter((order) => {
      const matchesKeyword = !normalizedKeyword || `${order.orderNo} ${order.username}`.toLowerCase().includes(normalizedKeyword);
      const matchesStatus = statusFilter === "ALL" || order.status === statusFilter;
      return matchesKeyword && matchesStatus;
    });
  }, [keyword, orders, statusFilter]);

  const shipmentValid = carrier.trim().length > 0 && trackingNo.trim().length > 0;

  function openShipping(order: AdminOrder) {
    setShippingOrder(order);
    setCarrier(order.shipment?.carrier || "");
    setTrackingNo(order.shipment?.trackingNo || "");
    setError("");
  }

  function closeShipping() {
    if (busy) return;
    setShippingOrder(undefined);
    setCarrier("");
    setTrackingNo("");
  }

  async function submitShipping(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!shippingOrder || !shipmentValid) return;
    setBusy(true);
    setError("");
    try {
      const updated = await api.adminShipOrder(shippingOrder.orderNo, carrier.trim(), trackingNo.trim());
      setOrders((items) => items.map((item) => item.orderNo === updated.orderNo ? updated : item));
      setSelected((current) => current?.orderNo === updated.orderNo ? updated : current);
      closeShipping();
    } catch (value) {
      setError(value instanceof Error ? value.message : T.shipFailed);
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-list-page admin-orders-page">
    <header className="admin-page-heading"><div><p>ORDER OPERATIONS</p><h1>{T.title}</h1><span>{T.subtitle}</span></div></header>
    <div className="admin-inventory-summary" aria-label="order-summary"><article><ClipboardList size={20}/><div><strong>{orders.length}</strong><span>{T.allOrders}</span></div></article><article className={orders.some(canShip) ? "is-warning" : ""}><Truck size={20}/><div><strong>{orders.filter(canShip).length}</strong><span>{T.pendingShipment}</span></div></article></div>
    <div className="admin-filter-bar"><label className="admin-search"><Search size={17}/><input aria-label={T.searchOrder} value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder={T.searchPlaceholder}/></label><label>{T.status}<select aria-label={T.statusFilter} value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as OrderStatusFilter)}><option value="ALL">{T.statusFilter.replace("??", "")}</option><option value="UNPAID">{T.unpaid}</option><option value="PAID">{T.pendingShipment}</option><option value="SHIPPED">{T.shipped}</option><option value="COMPLETED">{T.completed}</option><option value="CANCELLED">{T.cancelled}</option></select></label><span>{T.currentPrefix} {visibleOrders.length} {T.currentSuffix}</span></div>
    {error && <p className="admin-inline-error">{error}</p>}
    <AdminDataTable headers={[T.orderNo, T.user, T.status, T.payment, T.items, T.amount, T.createdAt, T.actions]} emptyText={T.empty} rows={visibleOrders.map((order) => ({ key: order.orderNo, cells: [<code className="admin-order-no">{order.orderNo}</code>, <strong>{order.username}</strong>, renderOrderStatus(order), <AdminStatusBadge status={order.paymentStatus === "PAID" ? "ACTIVE" : "DRAFT"} label={order.paymentStatus === "PAID" ? T.paid : T.notPaid} />, `${order.itemCount} ${T.pieces}`, money.format(order.totalAmount), formatDate(order.createdAt), <div className="admin-row-actions"><button type="button" onClick={() => setSelected(order)}>{T.view}</button>{canShip(order) && <button type="button" onClick={() => openShipping(order)}>{T.ship}</button>}</div>] }))} />
    {selected && <div className="admin-drawer-backdrop" onClick={() => setSelected(undefined)}><aside className="admin-drawer" role="dialog" aria-modal="true" aria-labelledby="order-detail-title" onClick={(event) => event.stopPropagation()}><header><div><p>ORDER DETAIL</p><h2 id="order-detail-title">{T.detailTitle}</h2></div><button type="button" onClick={() => setSelected(undefined)}>{T.close}</button></header><dl className="admin-detail-grid"><div><dt>{T.orderNo}</dt><dd>{selected.orderNo}</dd></div><div><dt>{T.user}</dt><dd>{selected.username}</dd></div><div><dt>{T.status}</dt><dd>{statusLabels[selected.status] || selected.status}</dd></div><div><dt>{T.payment}</dt><dd>{selected.paymentStatus === "PAID" ? T.paid : T.notPaid}</dd></div><div><dt>{T.itemCount}</dt><dd>{selected.itemCount} {T.pieces}</dd></div><div><dt>{T.amount}</dt><dd>{money.format(selected.totalAmount)}</dd></div><div className="admin-detail-wide"><dt>{T.address}</dt><dd>{selected.addressSummary || T.noAddress}</dd></div><div className="admin-detail-wide"><dt>{T.shipment}</dt><dd>{selected.shipment ? `${selected.shipment.carrier} ? ${selected.shipment.trackingNo}` : T.noShipment}</dd></div></dl></aside></div>}
    {shippingOrder && <div className="admin-dialog-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="order-shipping-title" className="admin-dialog"><h2 id="order-shipping-title">{T.shipDialog}</h2><p><strong>{shippingOrder.orderNo}</strong><br/>{T.shipTip}</p><form className="admin-adjustment-form" onSubmit={(event) => void submitShipping(event)}><label>{T.carrier}<input aria-label={T.carrier} value={carrier} onChange={(event) => setCarrier(event.target.value)} placeholder={T.carrierPlaceholder}/></label><label>{T.trackingNo}<input aria-label={T.trackingNo} value={trackingNo} onChange={(event) => setTrackingNo(event.target.value)} placeholder={T.trackingPlaceholder}/></label><footer><button type="button" onClick={closeShipping}>{T.cancel}</button><button className="admin-primary-button" type="submit" disabled={!shipmentValid || busy}>{busy ? T.submitting : T.confirmShip}</button></footer></form></section></div>}
  </section>;
}
