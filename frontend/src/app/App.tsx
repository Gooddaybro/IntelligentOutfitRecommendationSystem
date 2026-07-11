import { Bot, Layers3, LogOut, PackageSearch, ReceiptText, ShoppingCart, UserRound } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useAssistantShoppingState } from "../features/assistant/assistantState";
import { AuthPanel } from "../features/auth/AuthPanel";
import { useAuthSession } from "../features/auth/useAuthSession";
import { CartDrawer } from "../features/cart/CartDrawer";
import { useCartState } from "../features/cart/useCartState";
import { ConfirmActionDialog } from "../features/commerce-action/ConfirmActionDialog";
import { useCommerceAction } from "../features/commerce-action/useCommerceAction";
import { AiShoppingPage } from "../pages/AiShoppingPage";
import { CartPage } from "../pages/CartPage";
import { OrdersPage } from "../pages/OrdersPage";
import { ProductBrowsePage } from "../pages/ProductBrowsePage";
import { ProfilePreferencesPage } from "../pages/ProfilePreferencesPage";

type ViewKey = "ai" | "browse" | "cart" | "orders" | "profile";

export function App() {
  const [view, setView] = useState<ViewKey>("ai");
  const [isEntered, setIsEntered] = useState(false);
  const [isEntering, setIsEntering] = useState(false);
  const cart = useCartState();
  const assistant = useAssistantShoppingState();
  const clearCart = cart.clear;
  const resetAssistant = assistant.reset;

  const resetSessionState = useCallback(() => {
    clearCart();
    resetAssistant();
  }, [clearCart, resetAssistant]);

  const auth = useAuthSession({
    onAuthenticated: cart.refresh,
    onSessionCleared: resetSessionState
  });

  useEffect(() => {
    if (!auth.user) {
      setIsEntered(false);
      setIsEntering(false);
      return;
    }

    let timeout: number | undefined;
    const frame = window.requestAnimationFrame(() => {
      setIsEntered(true);
      setIsEntering(true);
      timeout = window.setTimeout(() => setIsEntering(false), 900);
    });

    return () => {
      window.cancelAnimationFrame(frame);
      if (timeout !== undefined) {
        window.clearTimeout(timeout);
      }
    };
  }, [auth.user]);

  const commerce = useCommerceAction({
    onCartItemsChange: cart.setItems,
    onOrderCreated: () => setView("orders")
  });

  function logout() {
    commerce.clear();
    setIsEntered(false);
    setIsEntering(false);
    auth.clearSession();
    setView("ai");
  }

  if (!auth.user) {
    return (
      <main className="auth-shell">
        <AuthPanel onLogin={auth.login} onRegister={auth.register} error={auth.error} isBusy={auth.isBusy} />
      </main>
    );
  }

  return (
    <div className={`app-shell${isEntered ? " is-entered" : ""}${isEntering ? " is-entering" : ""}`} data-testid="app-shell">
      <header className="topbar" data-testid="app-topbar">
        <div className="brand-lockup">
          <Layers3 size={26} />
          <div>
            <p className="brand-title">NOIR<span>.AI</span></p>
            <p className="eyebrow">智能穿搭工作台</p>
          </div>
        </div>
        <nav className="topnav" aria-label="主导航">
          <button
            className={view === "ai" ? "active" : ""}
            data-testid="nav-ai"
            onClick={() => setView("ai")}
            title="AI 导购工作台"
          >
            <Bot size={18} />
            <span>AI 导购工作台</span>
          </button>
          <button
            className={view === "browse" ? "active" : ""}
            data-testid="nav-browse"
            onClick={() => setView("browse")}
            title="传统浏览"
          >
            <PackageSearch size={18} />
            <span>商品中心</span>
          </button>
          <button
            className={view === "cart" ? "active" : ""}
            data-testid="nav-cart"
            onClick={() => setView("cart")}
            title="购物车"
          >
            <ShoppingCart size={18} />
            <span data-testid="cart-count">购物车 {cart.count}</span>
          </button>
          <button
            className={view === "orders" ? "active" : ""}
            data-testid="nav-orders"
            onClick={() => setView("orders")}
            title="订单"
          >
            <ReceiptText size={18} />
            <span>订单</span>
          </button>
          <button
            className={view === "profile" ? "active" : ""}
            data-testid="nav-profile"
            onClick={() => setView("profile")}
            title="我的偏好"
          >
            <UserRound size={18} />
            <span>我的偏好</span>
          </button>
        </nav>
        <div className="user-area">
          <span>{auth.user.username}</span>
          <button className="icon-button" onClick={logout} title="退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {commerce.status && <div className="status-line" data-testid="status-line">{commerce.status}</div>}

      {view === "ai" && (
        <AiShoppingPage
          chatState={assistant.chatState}
          recommendations={assistant.recommendations}
          setRecommendations={assistant.setRecommendations}
          recommendationMeta={assistant.recommendationMeta}
          setRecommendationMeta={assistant.setRecommendationMeta}
          recommendationsLoaded={assistant.recommendationsLoaded}
          setRecommendationsLoaded={assistant.setRecommendationsLoaded}
          isRecommendationsLoading={assistant.recommendationsLoading}
          setIsRecommendationsLoading={assistant.setRecommendationsLoading}
          onAction={commerce.setPendingAction}
          onRefreshCart={cart.refresh}
        />
      )}
      {view === "browse" && <ProductBrowsePage onAction={commerce.setPendingAction} />}
      {view === "cart" && <CartPage items={cart.items} onItemsChange={cart.setItems} onOrderCreated={() => setView("orders")} />}
      {view === "orders" && <OrdersPage />}
      {view === "profile" && <ProfilePreferencesPage />}

      <CartDrawer items={cart.items} onOpenCart={() => setView("cart")} />
      <ConfirmActionDialog
        action={commerce.pendingAction}
        isBusy={commerce.isBusy}
        onCancel={() => commerce.setPendingAction(null)}
        onConfirm={commerce.confirm}
      />
    </div>
  );
}
