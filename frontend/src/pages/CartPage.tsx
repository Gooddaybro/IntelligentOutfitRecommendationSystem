import { Minus, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { api } from "../shared/api/client";
import type { CartItem } from "../shared/api/types";

type CartPageProps = {
  items: CartItem[];
  onItemsChange: (items: CartItem[]) => void;
  onOrderCreated: () => void;
};

export function CartPage({ items, onItemsChange, onOrderCreated }: CartPageProps) {
  const [selectedSkuIds, setSelectedSkuIds] = useState<number[]>(items.map((item) => item.skuId));
  const [error, setError] = useState("");
  const [isBusy, setIsBusy] = useState(false);
  const total = useMemo(
    () => items.filter((item) => selectedSkuIds.includes(item.skuId)).reduce((sum, item) => sum + item.salePrice * item.quantity, 0),
    [items, selectedSkuIds]
  );

  async function updateQuantity(item: CartItem, quantity: number) {
    if (quantity < 1) {
      return;
    }
    onItemsChange(await api.updateCartItem(item.skuId, quantity));
  }

  async function removeItem(skuId: number) {
    onItemsChange(await api.removeCartItem(skuId));
    setSelectedSkuIds((current) => current.filter((id) => id !== skuId));
  }

  async function createOrder() {
    setError("");
    setIsBusy(true);
    try {
      await api.createOrder(selectedSkuIds);
      onItemsChange(await api.cart());
      onOrderCreated();
    } catch (orderError) {
      setError(orderError instanceof Error ? orderError.message : "创建订单失败");
    } finally {
      setIsBusy(false);
    }
  }

  return (
    <main className="workbench cart-layout">
      <section className="catalog-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">购物车结算</p>
            <h2>选择要下单的商品</h2>
          </div>
          <strong>￥{total.toFixed(2)}</strong>
        </div>
        <div className="cart-list" data-testid="cart-list">
          {items.map((item) => (
            <article key={item.skuId} className="cart-row" data-testid="cart-row">
              <input
                type="checkbox"
                checked={selectedSkuIds.includes(item.skuId)}
                onChange={(event) =>
                  setSelectedSkuIds((current) =>
                    event.target.checked ? [...current, item.skuId] : current.filter((id) => id !== item.skuId)
                  )
                }
              />
              <div className="product-image small">
                {item.mainImageUrl ? (
                  <img src={item.mainImageUrl} alt={item.name} onError={(event) => { event.currentTarget.style.display = "none"; }} />
                ) : (
                  <span>暂无图片</span>
                )}
              </div>
              <div className="cart-main">
                <h3>{item.name}</h3>
                <p>{item.color || "默认颜色"} · {item.size || "默认尺码"} · ￥{item.salePrice}</p>
              </div>
              <div className="quantity-stepper">
                <button onClick={() => void updateQuantity(item, item.quantity - 1)} title="减少数量">
                  <Minus size={15} />
                </button>
                <span>{item.quantity}</span>
                <button onClick={() => void updateQuantity(item, item.quantity + 1)} title="增加数量">
                  <Plus size={15} />
                </button>
              </div>
              <button className="icon-button" onClick={() => void removeItem(item.skuId)} title="移除">
                <Trash2 size={16} />
              </button>
            </article>
          ))}
        </div>
        {error && <p className="error-text">{error}</p>}
        <button
          className="primary-button checkout-button"
          data-testid="checkout-submit"
          disabled={selectedSkuIds.length === 0 || isBusy}
          onClick={() => void createOrder()}
        >
          创建订单
        </button>
      </section>
    </main>
  );
}
