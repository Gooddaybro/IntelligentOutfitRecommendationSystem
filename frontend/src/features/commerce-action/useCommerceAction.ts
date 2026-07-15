import { useCallback, useState } from "react";
import { api } from "../../shared/api/client";
import type { CartItem, OrderResponse } from "../../shared/api/types";
import type { PendingCommerceAction } from "./commerceActions";

type UseCommerceActionOptions = {
  onCartItemsChange: (items: CartItem[]) => void;
  onOrderCreated: () => void;
};

export function useCommerceAction({ onCartItemsChange, onOrderCreated }: UseCommerceActionOptions) {
  const [pendingAction, setPendingAction] = useState<PendingCommerceAction | null>(null);
  const [status, setStatus] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  const confirm = useCallback(async (): Promise<OrderResponse | null> => {
    if (!pendingAction) {
      return null;
    }

    setIsBusy(true);
    try {
      if (pendingAction.kind === "BUY_NOW") {
        const order = await api.buyNow(
          pendingAction.skuId,
          pendingAction.quantity,
          pendingAction.recommendationId
        );
        setStatus(`已生成订单 ${order.orderNo}`);
        setPendingAction(null);
        onOrderCreated();
        return order;
      }

      const items = await api.addCartItem(
        pendingAction.skuId,
        pendingAction.quantity,
        pendingAction.recommendationId
      );
      onCartItemsChange(items);
      setStatus("已加入购物车");
      setPendingAction(null);
      return null;
    } finally {
      setIsBusy(false);
    }
  }, [onCartItemsChange, onOrderCreated, pendingAction]);

  const clear = useCallback(() => {
    setPendingAction(null);
    setStatus("");
    setIsBusy(false);
  }, []);

  return {
    pendingAction,
    setPendingAction,
    status,
    isBusy,
    confirm,
    clear
  };
}
