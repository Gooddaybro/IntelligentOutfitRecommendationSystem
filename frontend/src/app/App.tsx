import { Bot, LogOut, PackageSearch, ReceiptText, ShoppingCart } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthPanel } from "../features/auth/AuthPanel";
import { CartDrawer } from "../features/cart/CartDrawer";
import { ConfirmActionDialog } from "../features/commerce-action/ConfirmActionDialog";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { AiShoppingPage } from "../pages/AiShoppingPage";
import { CartPage } from "../pages/CartPage";
import { OrdersPage } from "../pages/OrdersPage";
import { ProductBrowsePage } from "../pages/ProductBrowsePage";
import { api, clearAuthTokens, getAccessToken, setAuthTokens } from "../shared/api/client";
import type { CartItem, CurrentUserResponse, OrderResponse } from "../shared/api/types";

type ViewKey = "ai" | "browse" | "cart" | "orders";

export function App() {
  const [view, setView] = useState<ViewKey>("ai");
  const [user, setUser] = useState<CurrentUserResponse | null>(null);
  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [pendingAction, setPendingAction] = useState<PendingCommerceAction | null>(null);
  const [status, setStatus] = useState("");
  const [authError, setAuthError] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  const cartCount = useMemo(() => cartItems.reduce((total, item) => total + item.quantity, 0), [cartItems]);

  const refreshCart = useCallback(async () => {
    if (!getAccessToken()) {
      return;
    }
    setCartItems(await api.cart());
  }, []);

  const loadUser = useCallback(async () => {
    if (!getAccessToken()) {
      return;
    }
    try {
      const currentUser = await api.me();
      setUser(currentUser);
      await refreshCart();
    } catch {
      clearAuthTokens();
      setUser(null);
    }
  }, [refreshCart]);

  useEffect(() => {
    void loadUser();
  }, [loadUser]);

  async function handleLogin(username: string, password: string) {
    setAuthError("");
    setIsBusy(true);
    try {
      const tokens = await api.login(username, password);
      setAuthTokens(tokens);
      await loadUser();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "登录失败");
    } finally {
      setIsBusy(false);
    }
  }

  async function handleRegister(username: string, password: string, email?: string) {
    setAuthError("");
    setIsBusy(true);
    try {
      await api.register(username, password, email);
      const tokens = await api.login(username, password);
      setAuthTokens(tokens);
      await loadUser();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "注册失败");
    } finally {
      setIsBusy(false);
    }
  }

  async function confirmCommerceAction(): Promise<OrderResponse | null> {
    if (!pendingAction) {
      return null;
    }
    setIsBusy(true);
    try {
      if (pendingAction.kind === "BUY_NOW") {
        const order = await api.buyNow(pendingAction.skuId, pendingAction.quantity);
        setStatus(`已生成订单 ${order.orderNo}`);
        setView("orders");
        setPendingAction(null);
        return order;
      }
      const nextCart = await api.addCartItem(pendingAction.skuId, pendingAction.quantity);
      setCartItems(nextCart);
      setStatus("已加入购物车");
      setPendingAction(null);
      return null;
    } finally {
      setIsBusy(false);
    }
  }

  function logout() {
    clearAuthTokens();
    setUser(null);
    setCartItems([]);
    setView("ai");
  }

  if (!user) {
    return (
      <main className="auth-shell">
        <AuthPanel onLogin={handleLogin} onRegister={handleRegister} error={authError} isBusy={isBusy} />
      </main>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Intelligent Outfit Recommendation System</p>
          <h1>AI 服装导购工作台</h1>
        </div>
        <nav className="topnav" aria-label="主导航">
          <button className={view === "ai" ? "active" : ""} onClick={() => setView("ai")} title="AI 推荐">
            <Bot size={18} />
            <span>AI 推荐</span>
          </button>
          <button className={view === "browse" ? "active" : ""} onClick={() => setView("browse")} title="传统浏览">
            <PackageSearch size={18} />
            <span>传统浏览</span>
          </button>
          <button className={view === "cart" ? "active" : ""} onClick={() => setView("cart")} title="购物车">
            <ShoppingCart size={18} />
            <span>购物车 {cartCount}</span>
          </button>
          <button className={view === "orders" ? "active" : ""} onClick={() => setView("orders")} title="订单">
            <ReceiptText size={18} />
            <span>订单</span>
          </button>
        </nav>
        <div className="user-area">
          <span>{user.username}</span>
          <button className="icon-button" onClick={logout} title="退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {status && <div className="status-line">{status}</div>}

      {view === "ai" && (
        <AiShoppingPage
          cartItems={cartItems}
          onAction={setPendingAction}
          onRefreshCart={refreshCart}
          onOpenCart={() => setView("cart")}
        />
      )}
      {view === "browse" && <ProductBrowsePage onAction={setPendingAction} />}
      {view === "cart" && <CartPage items={cartItems} onItemsChange={setCartItems} onOrderCreated={() => setView("orders")} />}
      {view === "orders" && <OrdersPage />}

      <CartDrawer items={cartItems} onOpenCart={() => setView("cart")} />
      <ConfirmActionDialog
        action={pendingAction}
        isBusy={isBusy}
        onCancel={() => setPendingAction(null)}
        onConfirm={confirmCommerceAction}
      />
    </div>
  );
}
