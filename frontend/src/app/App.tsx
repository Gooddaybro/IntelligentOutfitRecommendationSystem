import { useCallback, useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { useAssistantShoppingState } from "../features/assistant/assistantState";
import { AuthPanel } from "../features/auth/AuthPanel";
import { useAuthSession } from "../features/auth/useAuthSession";
import { CartDrawer } from "../features/cart/CartDrawer";
import { useCartState } from "../features/cart/useCartState";
import { ConfirmActionDialog } from "../features/commerce-action/ConfirmActionDialog";
import { useCommerceAction } from "../features/commerce-action/useCommerceAction";
import { AdminDashboardPage } from "../pages/AdminDashboardPage";
import { AiShoppingPage } from "../pages/AiShoppingPage";
import { CartPage } from "../pages/CartPage";
import { HomePage } from "../pages/HomePage";
import { OrdersPage } from "../pages/OrdersPage";
import { ProductBrowsePage } from "../pages/ProductBrowsePage";
import { ProductDetailPage } from "../pages/ProductDetailPage";
import { ProfilePreferencesPage } from "../pages/ProfilePreferencesPage";
import { AdminShell } from "./AdminShell";
import { CustomerShell } from "./CustomerShell";

export function App() {
  const [isEntered, setIsEntered] = useState(false);
  const [isEntering, setIsEntering] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const cart = useCartState();
  const assistant = useAssistantShoppingState();

  const resetSessionState = useCallback(() => {
    cart.clear();
    assistant.reset();
  }, [cart.clear, assistant.reset]);

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
      if (!location.pathname.startsWith("/app") && !location.pathname.startsWith("/admin")) {
        navigate("/app/home", { replace: true });
      }
      timeout = window.setTimeout(() => setIsEntering(false), 500);
    });

    return () => {
      window.cancelAnimationFrame(frame);
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [auth.user, location.pathname, navigate]);

  const commerce = useCommerceAction({
    onCartItemsChange: cart.setItems,
    onOrderCreated: () => navigate("/app/orders")
  });

  function logout() {
    commerce.clear();
    setIsEntered(false);
    setIsEntering(false);
    auth.clearSession();
    navigate("/", { replace: true });
  }

  if (!auth.user) {
    return <main className="auth-shell"><AuthPanel onLogin={auth.login} onRegister={auth.register} error={auth.error} isBusy={auth.isBusy} /></main>;
  }

  const aiPage = (
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
  );

  return (
    <div className={`${isEntered ? "is-entered" : ""}${isEntering ? " is-entering" : ""}`} data-testid="app-shell">
      {commerce.status && <div className="status-line" data-testid="status-line">{commerce.status}</div>}
      <Routes>
        <Route path="/app" element={<CustomerShell user={auth.user} cartCount={cart.count} onLogout={logout} />}>
          <Route index element={<Navigate to="home" replace />} />
          <Route path="home" element={<HomePage username={auth.user.username} cartCount={cart.count} recommendations={assistant.recommendations} />} />
          <Route path="ai" element={aiPage} />
          <Route path="products" element={<ProductBrowsePage onAction={commerce.setPendingAction} />} />
          <Route path="products/:spuId" element={<ProductDetailPage onAction={commerce.setPendingAction} />} />
          <Route path="cart" element={<CartPage items={cart.items} onItemsChange={cart.setItems} onOrderCreated={() => navigate("/app/orders")} />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="profile/*" element={<ProfilePreferencesPage />} />
        </Route>
        <Route
          path="/admin"
          element={auth.user.role?.toUpperCase().includes("ADMIN")
            ? <AdminShell user={auth.user} onLogout={logout} />
            : <Navigate to="/app/home" replace />}
        >
          <Route index element={<AdminDashboardPage />} />
          <Route path="*" element={<section className="admin-placeholder"><h1>管理模块正在落位</h1><p>该页面将在前端 F3 阶段接入可演示操作。</p></section>} />
        </Route>
        <Route path="*" element={<Navigate to="/app/home" replace />} />
      </Routes>

      <CartDrawer items={cart.items} onOpenCart={() => navigate("/app/cart")} />
      <ConfirmActionDialog
        action={commerce.pendingAction}
        isBusy={commerce.isBusy}
        onCancel={() => commerce.setPendingAction(null)}
        onConfirm={commerce.confirm}
      />
    </div>
  );
}
