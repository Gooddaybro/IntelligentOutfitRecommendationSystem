import { ShoppingCart } from "lucide-react";
import type { CartItem } from "../../shared/api/types";

type CartDrawerProps = {
  items: CartItem[];
  onOpenCart: () => void;
};

export function CartDrawer({ items, onOpenCart }: CartDrawerProps) {
  const total = items.reduce((sum, item) => sum + item.salePrice * item.quantity, 0);

  return (
    <aside className="floating-cart" aria-label="购物车摘要">
      <button onClick={onOpenCart} title="打开购物车">
        <ShoppingCart size={18} />
        <span>{items.length} 款</span>
        <strong>￥{total.toFixed(2)}</strong>
      </button>
    </aside>
  );
}
