import { useCallback, useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { useAssistantShoppingState } from "../features/assistant/assistantState";
import { AuthPanel } from "../features/auth/AuthPanel";
import { useAuthSession } from "../features/auth/useAuthSession";
import { CartDrawer } from "../features/cart/CartDrawer";
import { useCartState } from "../features/cart/useCartState";
import { ConfirmActionDialog } from "../features/commerce-action/ConfirmActionDialog";
import { useCommerceAction } from "../features/commerce-action/useCommerceAction";
import { serializeCheckoutSkuIds } from "../features/checkout/checkoutSelection";
import { api, IS_MOCK_MODE } from "../shared/api/client";
import { AdminDashboardPage } from "../pages/AdminDashboardPage";
import { AdminProductsPage } from "../pages/admin/AdminProductsPage";
import { AdminProductFormPage } from "../pages/admin/AdminProductFormPage";
import { AdminCategoriesPage } from "../pages/admin/AdminCategoriesPage";
import { AdminInventoryPage } from "../pages/admin/AdminInventoryPage";
import { AdminOrdersPage } from "../pages/admin/AdminOrdersPage";
import { AdminUsersPage } from "../pages/admin/AdminUsersPage";
import { AdminAnalyticsPage } from "../pages/admin/AdminAnalyticsPage";
import { AdminAuditLogsPage } from "../pages/admin/AdminAuditLogsPage";
import { AiShoppingPage } from "../pages/AiShoppingPage";
import { CartPage } from "../pages/CartPage";
import { CheckoutPage } from "../pages/CheckoutPage";
import { HomePage } from "../pages/HomePage";
import { OrdersPage } from "../pages/OrdersPage";
import { OrderDetailPage } from "../pages/OrderDetailPage";
import { PaymentResultPage } from "../pages/PaymentResultPage";
import { ProductBrowsePage } from "../pages/ProductBrowsePage";
import { ProductDetailPage } from "../pages/ProductDetailPage";
import { ProfilePreferencesPage } from "../pages/ProfilePreferencesPage";
import { ProfileCenterPage } from "../pages/ProfileCenterPage";
import { ProfileAccountPage } from "../pages/ProfileAccountPage";
import { AddressBookPage } from "../pages/AddressBookPage";
import { FavoritesPage } from "../pages/FavoritesPage";
import { AccountSecurityPage } from "../pages/AccountSecurityPage";
import { AdminShell } from "./AdminShell";
import { CustomerShell } from "./CustomerShell";
import { isCustomerPath } from "./navigation";

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
    if (!auth.user || assistant.recommendationsLoaded || assistant.recommendationsLoading) return;
    let cancelled = false;
    assistant.setRecommendationsLoading(true);
    api.recommendationCandidates({})
      .then((items) => {
        if (cancelled) return;
        assistant.setRecommendations(items);
        assistant.setRecommendationsLoaded(true);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) assistant.setRecommendationsLoading(false);
      });
    return () => { cancelled = true; };
  }, [auth.user?.userId, assistant.recommendationsLoaded]);

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
        <Route path="/app" element={<CustomerShell user={auth.user} cartCount={cart.count} onLogout={logout} isDemoMode={IS_MOCK_MODE} />}>
          <Route index element={<Navigate to="home" replace />} />
          <Route path="home" element={<HomePage username={auth.user.username} cartCount={cart.count} recommendations={assistant.recommendations} />} />
          <Route path="ai" element={aiPage} />
          <Route path="products" element={<ProductBrowsePage onAction={commerce.setPendingAction} />} />
          <Route path="products/:spuId" element={<ProductDetailPage onAction={commerce.setPendingAction} />} />
          <Route path="cart" element={<CartPage items={cart.items} onItemsChange={cart.setItems} onCheckout={(skuIds) => navigate(`/app/checkout?skuIds=${serializeCheckoutSkuIds(skuIds)}`)} />} />
          <Route path="checkout" element={<CheckoutPage onOrderCreated={(order) => { void cart.refresh(); navigate(`/app/payments/${order.orderNo}`); }} />} />
          <Route path="payments/:orderNo" element={<PaymentResultPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:orderNo" element={<OrderDetailPage />} />
          <Route path="profile" element={<ProfileCenterPage />}>
            <Route index element={<Navigate to="account" replace />} />
            <Route path="account" element={<ProfileAccountPage />} />
            <Route path="wardrobe" element={<ProfilePreferencesPage />} />
            <Route path="favorites" element={<FavoritesPage />} />
            <Route path="addresses" element={<AddressBookPage />} />
            <Route path="security" element={<AccountSecurityPage />} />
          </Route>
        </Route>
        <Route
          path="/admin"
          element={auth.user.role?.toUpperCase().includes("ADMIN")
            ? <AdminShell user={auth.user} onLogout={logout} />
            : <Navigate to="/app/home" replace />}
        >
          <Route index element={<AdminDashboardPage />} />
          <Route path="products" element={<AdminProductsPage />} />
          <Route path="products/new" element={<AdminProductFormPage />} />
          <Route path="products/:spuId/edit" element={<AdminProductFormPage />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="inventory" element={<AdminInventoryPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="analytics" element={<AdminAnalyticsPage />} />
          <Route path="audit-logs" element={<AdminAuditLogsPage />} />
          <Route path="*" element={<section className="admin-placeholder"><h1>管理模块正在落位</h1><p>该页面将在前端 F3 阶段接入可演示操作。</p></section>} />
        </Route>
        <Route path="*" element={<Navigate to="/app/home" replace />} />
      </Routes>

      {isCustomerPath(location.pathname) && <>
        <CartDrawer items={cart.items} onOpenCart={() => navigate("/app/cart")} />
        <ConfirmActionDialog
          action={commerce.pendingAction}
          isBusy={commerce.isBusy}
          onCancel={() => commerce.setPendingAction(null)}
          onConfirm={commerce.confirm}
        />
      </>}
    </div>
  );
}
