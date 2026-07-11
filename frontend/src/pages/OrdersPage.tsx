import { CreditCard, RefreshCcw } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../shared/api/client";
import type { OrderResponse } from "../shared/api/types";

export function OrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [error, setError] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  async function loadOrders() {
    setError("");
    setOrders(await api.orders());
  }

  useEffect(() => {
    void loadOrders().catch((loadError) => setError(loadError instanceof Error ? loadError.message : "订单加载失败"));
  }, []);

  async function pay(orderNo: string) {
    setIsBusy(true);
    setError("");
    try {
      await api.pay(orderNo, "MOCK");
      await loadOrders();
    } catch (payError) {
      setError(payError instanceof Error ? payError.message : "支付失败");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="workbench cart-layout noir-page noir-page--orders" data-testid="orders-page">
      <section className="catalog-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">订单中心</p>
            <h2>我的订单</h2>
          </div>
          <button onClick={() => void loadOrders()} title="刷新订单">
            <RefreshCcw size={16} />
            刷新
          </button>
        </div>
        {error && <p className="error-text">{error}</p>}
        <div className="order-list" data-testid="order-list">
          {orders.map((order) => (
            <article key={order.orderNo} className="order-row" data-testid="order-row">
              <div>
                <p className="eyebrow">{order.status}</p>
                <h3>{order.orderNo}</h3>
                <p>{order.items.map((item) => `${item.productName} x${item.quantity}`).join("，")}</p>
              </div>
              <strong>￥{order.totalAmount}</strong>
              {order.status === "UNPAID" && (
                <button
                  className="primary-button"
                  data-testid="mock-pay-submit"
                  onClick={() => void pay(order.orderNo)}
                  disabled={isBusy}
                >
                  <CreditCard size={16} />
                  Mock 支付
                </button>
              )}
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
