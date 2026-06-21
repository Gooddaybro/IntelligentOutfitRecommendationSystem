import { Bot, LogOut, PackageSearch, ReceiptText, ShoppingCart } from "lucide-react";
import { useCallback, useState } from "react";
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

type ViewKey = "ai" | "browse" | "cart" | "orders";

export function App() {
  const [view, setView] = useState<ViewKey>("ai");
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

  const commerce = useCommerceAction({
    onCartItemsChange: cart.setItems,
    onOrderCreated: () => setView("orders")
  });

  function logout() {
    commerce.clear();
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
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Intelligent Outfit Recommendation System</p>
          <h1>AI 服装导购工作台</h1>
        </div>
        <nav className="topnav" aria-label="主导航">
          <button
            className={view === "ai" ? "active" : ""}
            data-testid="nav-ai"
            onClick={() => setView("ai")}
            title="AI 推荐"
          >
            <Bot size={18} />
            <span>AI 推荐</span>
          </button>
          <button
            className={view === "browse" ? "active" : ""}
            data-testid="nav-browse"
            onClick={() => setView("browse")}
            title="传统浏览"
          >
            <PackageSearch size={18} />
            <span>传统浏览</span>
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
          cartItems={cart.items}
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
          onOpenCart={() => setView("cart")}
        />
      )}
      {view === "browse" && <ProductBrowsePage onAction={commerce.setPendingAction} />}
      {view === "cart" && <CartPage items={cart.items} onItemsChange={cart.setItems} onOrderCreated={() => setView("orders")} />}
      {view === "orders" && <OrdersPage />}

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
