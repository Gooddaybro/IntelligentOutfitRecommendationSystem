import { useCallback, useMemo, useState } from "react";
import { api, getAccessToken } from "../../shared/api/client";
import type { CartItem } from "../../shared/api/types";

export function useCartState() {
  const [items, setItems] = useState<CartItem[]>([]);

  const count = useMemo(() => items.reduce((total, item) => total + item.quantity, 0), [items]);

  const refresh = useCallback(async () => {
    if (!getAccessToken()) {
      return;
    }
    setItems(await api.cart());
  }, []);

  const clear = useCallback(() => {
    setItems([]);
  }, []);

  return {
    items,
    setItems,
    count,
    refresh,
    clear
  };
}
