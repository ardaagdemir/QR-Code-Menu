"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  addCartItem,
  ApiError,
  checkInWithQrToken,
  createPaymentIntent,
  getCart,
  getMenu,
  getPopularProductIds,
  removeCartItem,
  setGuestCount as setGuestCountRequest,
  type Cart,
  type Menu,
  type MenuProduct,
  type PaymentIntent,
  type TableVisit,
} from "@/lib/api";
import DishPlaceholderIcon from "@/components/ui/DishPlaceholderIcon";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import CartDrawer from "./CartDrawer";
import CategoryNav from "./CategoryNav";
import GuestCountSheet from "./GuestCountSheet";
import MenuSection from "./MenuSection";
import MenuSkeleton from "./MenuSkeleton";
import OrdersSheet from "./OrdersSheet";
import PaymentSheet from "./PaymentSheet";
import ProductOptionsSheet from "./ProductOptionsSheet";
import ProductRowSection from "./ProductRowSection";
import SearchBar from "./SearchBar";
import { useFavorites } from "./useFavorites";
import { useOrderHistory } from "./useOrderHistory";
import VisitHeader from "./VisitHeader";
import styles from "./page.module.css";

/** Gap-analysis #17: once skipped, don't re-interrupt the same visit with the same
 * automatic prompt again - the header control stays available to add it later. */
function guestCountPromptDismissedKey(tableVisitId: string): string {
  return `qrmenu.guestCountPromptDismissed.${tableVisitId}`;
}

type LoadState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "expired" }
  | { status: "ready"; visit: TableVisit; menu: Menu };

function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return "Bu QR kod geçersiz ya da artık aktif değil. Lütfen masadaki QR kodu tekrar okutun.";
  }
  return "Menü yüklenirken bir sorun oluştu. İnternet bağlantınızı kontrol edip tekrar deneyin.";
}

// Once the page is loaded, a 404 on a cart/checkout call means the caller's view of the
// visit/cart is stale and unrecoverable in place - either the TableVisit itself closed
// underneath the user (TTL, Gap-Analysis #13) or the draft order/product it referenced is
// gone. ApiError only carries the HTTP status (no machine-readable reason code), so the two
// causes can't be told apart client-side without a backend change - but the recovery is the
// same for both (reload -> re-check-in), so this is routed to one full-page state (Bölüm
// 19.2: "expired session/cart" durum ekranı) worded to hold for either cause.
function isStaleVisitError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

// The backend's synchronous, authoritative TableVisit expiry gate (60dk inactivity /
// 4 saat absolute lifetime - CustomerSessionService.getActiveTableVisitForOrdering)
// returns 410 Gone specifically for "visit still exists but can't order anymore",
// distinct from the 404 handled by isStaleVisitError (visit/cart genuinely gone) and
// from the 409s ProductNotOrderableException/OrderingNotAllowedException already use.
const VISIT_EXPIRED_MESSAGE = "Oturumunuz sona erdi. Yeni sipariş için masadaki QR kodunu tekrar okutun.";

function isVisitExpiredForOrderingError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 410;
}

function cartActionErrorMessage(error: unknown): string {
  if (isVisitExpiredForOrderingError(error)) {
    return VISIT_EXPIRED_MESSAGE;
  }
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "Bu ürün şu anda sipariş alınamıyor (tükenmiş ya da bu şubede satışta değil).";
    }
    if (error.status === 400) {
      return "Seçimler eksik veya hatalı görünüyor. Lütfen tekrar deneyin.";
    }
  }
  return "Sepete eklenirken bir sorun oluştu. Lütfen tekrar deneyin.";
}

function checkoutErrorMessage(error: unknown): string {
  // 404 (no payable cart / stale visit) is handled separately via isStaleVisitError before
  // this is called - it never reaches here.
  if (isVisitExpiredForOrderingError(error)) {
    return VISIT_EXPIRED_MESSAGE;
  }
  if (error instanceof ApiError && error.status === 409) {
    return "Bu şube şu anda sipariş kabul etmiyor (kapalı ya da çalışma saatleri dışında).";
  }
  return "Ödeme başlatılırken bir sorun oluştu. Lütfen tekrar deneyin.";
}

/**
 * QR karşılama + menü sayfası (docs/product-requirements.md Bölüm 4, ekranlar #1-5).
 * Sadece orkestrasyon: veri yükleme/hata/skeleton state'i ve alt component'lerin
 * bağlanması burada, görsel/etkileşim mantığı kendi component'lerinde (Bölüm 14:
 * "tek bir devasa sayfa component'i yerine küçük, tek sorumluluklu component'ler").
 */
export default function TableVisitPage() {
  const params = useParams<{ token: string }>();
  const token = params.token;

  const [state, setState] = useState<LoadState>({ status: "loading" });
  const [cart, setCart] = useState<Cart | null>(null);
  const [activeProduct, setActiveProduct] = useState<MenuProduct | null>(null);
  const [submittingCart, setSubmittingCart] = useState(false);
  const [cartActionError, setCartActionError] = useState<string | null>(null);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [activeCategoryId, setActiveCategoryId] = useState<string | null>(null);
  const [reloadNonce, setReloadNonce] = useState(0);
  const [paymentIntent, setPaymentIntent] = useState<PaymentIntent | null>(null);
  const [checkoutSubmitting, setCheckoutSubmitting] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  // orderTrackingToken is only ever returned once, on the response that creates the
  // order (Section 2) - captured here so it's still available after later cart/payment
  // responses stop including it, for the post-payment shareable tracking link.
  const [trackingToken, setTrackingToken] = useState<string | null>(null);
  const [ordersSheetOpen, setOrdersSheetOpen] = useState(false);
  const [guestCountSheetOpen, setGuestCountSheetOpen] = useState(false);
  const [guestCountSubmitting, setGuestCountSubmitting] = useState(false);
  const [guestCountError, setGuestCountError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  // Gerçek son 30 günlük satış verisinden - veri yoksa boş kalır, sahte sıralama yok.
  const [popularProductIds, setPopularProductIds] = useState<string[]>([]);

  // Favoriler backend'de yok, branch bazlı localStorage'da tutulur (hook Rules of
  // Hooks gereği loading/error early-return'lardan önce, koşulsuz çağrılmalı).
  const menuForFavorites = state.status === "ready" ? state.menu : null;
  const branchIdForFavorites = state.status === "ready" ? state.visit.branchId : "";
  const validProductIds = useMemo(
    () =>
      new Set(
        menuForFavorites ? menuForFavorites.categories.flatMap((category) => category.products.map((product) => product.id)) : [],
      ),
    [menuForFavorites],
  );
  const { favoriteIds, toggleFavorite } = useFavorites(branchIdForFavorites, validProductIds);
  // Siparişlerim: geçmiş orderTrackingToken'lar MASA (tableId) bazlı localStorage'da
  // kalıcı - branchId DEĞİL, çünkü aynı branch'teki farklı masaların sipariş geçmişi
  // birbirinden izole olmalı (Masa 8'de verilen sipariş Masa 9'da görünmemeli). Bu
  // component her (yeniden) mount'ta (ör. "Menüye Dön" navigasyonu) geçici React state'i
  // sıfırlansa da güncel listeyi doğrudan localStorage'dan okur.
  const tableIdForOrderHistory = state.status === "ready" ? state.visit.tableId : "";
  const { entries: orderHistoryEntries, addToken: addOrderHistoryToken, removeToken: removeOrderHistoryToken } =
    useOrderHistory(tableIdForOrderHistory);

  const retry = useCallback(() => {
    setState({ status: "loading" });
    setReloadNonce((n) => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        // QR only ever starts/continues a TableVisit - it never grants access to an
        // existing cart or order (docs/product-requirements.md Section 5).
        const visit = await checkInWithQrToken(token);
        const [menu, initialCart] = await Promise.all([getMenu(visit.branchId), getCart(visit.tableVisitId)]);
        if (!cancelled) {
          setState({ status: "ready", visit, menu });
          setCart(initialCart);
          setActiveCategoryId(menu.categories[0]?.id ?? null);
          // Gap-analysis #17: ask once per visit, low-friction - not if already answered
          // (visit.guestCount set) and not if the customer already skipped it this visit.
          if (visit.guestCount == null && sessionStorage.getItem(guestCountPromptDismissedKey(visit.tableVisitId)) == null) {
            setGuestCountSheetOpen(true);
          }
          // En Çok Tercih Edilenler: best-effort - başarısız olursa bölüm gizli kalır,
          // ana menü akışını bloklamaz/bozmaz.
          getPopularProductIds(visit.branchId)
            .then((ids) => {
              if (!cancelled) {
                setPopularProductIds(ids);
              }
            })
            .catch(() => {});
        }
      } catch (error) {
        if (!cancelled) {
          setState({ status: "error", message: loadErrorMessage(error) });
        }
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [token, reloadNonce]);

  // Scroll-spy: highlight whichever category chip matches the section in view.
  useEffect(() => {
    if (state.status !== "ready") {
      return;
    }
    const sections = state.menu.categories
      .map((category) => document.getElementById(`category-${category.id}`))
      .filter((el): el is HTMLElement => el !== null);
    if (sections.length === 0) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) {
          setActiveCategoryId(visible[0].target.id.replace("category-", ""));
        }
      },
      { rootMargin: "-130px 0px -65% 0px", threshold: 0 },
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [state]);

  if (state.status === "loading") {
    return (
      <main className={styles.page}>
        <MenuSkeleton />
      </main>
    );
  }

  if (state.status === "error") {
    return (
      <main className={styles.page}>
        <div className={styles.centeredState}>
          <ErrorState message={state.message} onRetry={retry} />
        </div>
      </main>
    );
  }

  if (state.status === "expired") {
    return (
      <main className={styles.page}>
        <div className={styles.centeredState}>
          <ErrorState
            title="Devam edilemiyor"
            message="Masa oturumunuz veya sepetiniz güncel görünmüyor. Devam etmek için menü yeniden yüklenecek."
            onRetry={retry}
          />
        </div>
      </main>
    );
  }

  const { visit, menu } = state;

  const productById = new Map<string, MenuProduct>(
    menu.categories.flatMap((category) => category.products).map((product) => [product.id, product]),
  );
  const favoriteProducts = Array.from(favoriteIds)
    .map((id) => productById.get(id))
    .filter((product): product is MenuProduct => Boolean(product));
  const popularProducts = popularProductIds
    .map((id) => productById.get(id))
    .filter((product): product is MenuProduct => Boolean(product));

  const normalizedQuery = searchQuery.trim().toLocaleLowerCase("tr");
  const isSearching = normalizedQuery.length > 0;
  const searchResults = isSearching
    ? menu.categories
        .map((category) => ({
          ...category,
          products: category.products.filter(
            (product) =>
              product.name.toLocaleLowerCase("tr").includes(normalizedQuery) ||
              (product.description?.toLocaleLowerCase("tr").includes(normalizedQuery) ?? false),
          ),
        }))
        .filter((category) => category.products.length > 0)
    : [];

  function scrollToCategory(categoryId: string) {
    setActiveCategoryId(categoryId);
    document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function handleOpenTracking() {
    setOrdersSheetOpen(true);
  }

  async function handleAddToCart(selectedOptionIds: string[], quantity: number) {
    if (!activeProduct) {
      return;
    }
    setSubmittingCart(true);
    setCartActionError(null);
    try {
      const updatedCart = await addCartItem(visit.tableVisitId, {
        productId: activeProduct.id,
        quantity,
        selectedOptionIds,
      });
      setCart(updatedCart);
      if (updatedCart.orderTrackingToken) {
        setTrackingToken(updatedCart.orderTrackingToken);
        addOrderHistoryToken(updatedCart.orderTrackingToken);
      }
      setActiveProduct(null);
    } catch (error) {
      if (isStaleVisitError(error)) {
        setState({ status: "expired" });
        return;
      }
      setCartActionError(cartActionErrorMessage(error));
    } finally {
      setSubmittingCart(false);
    }
  }

  async function handleRemoveItem(orderItemId: string) {
    setRemovingItemId(orderItemId);
    try {
      const updatedCart = await removeCartItem(visit.tableVisitId, orderItemId);
      setCart(updatedCart);
    } catch (error) {
      if (isStaleVisitError(error)) {
        setState({ status: "expired" });
        return;
      }
      // Best-effort otherwise: the item stays visible in the drawer, the user can retry.
    } finally {
      setRemovingItemId(null);
    }
  }

  async function handleCheckout() {
    setCheckoutSubmitting(true);
    setCheckoutError(null);
    try {
      const intent = await createPaymentIntent(visit.tableVisitId);
      setPaymentIntent(intent);
    } catch (error) {
      if (isStaleVisitError(error)) {
        setState({ status: "expired" });
        return;
      }
      setCheckoutError(checkoutErrorMessage(error));
    } finally {
      setCheckoutSubmitting(false);
    }
  }

  async function handleOrderPaid() {
    setPaymentIntent(null);
    // The order has left DRAFT (Section 6: AWAITING_PAYMENT -> PAID), so the cart is
    // now empty from the customer's point of view - re-fetch to reflect that.
    try {
      setCart(await getCart(visit.tableVisitId));
    } catch {
      setCart(null);
    }
  }

  function handleGuestCountSkip() {
    sessionStorage.setItem(guestCountPromptDismissedKey(visit.tableVisitId), "1");
    setGuestCountError(null);
    setGuestCountSheetOpen(false);
  }

  async function handleGuestCountConfirm(guestCount: number) {
    setGuestCountSubmitting(true);
    setGuestCountError(null);
    try {
      await setGuestCountRequest(visit.tableVisitId, guestCount);
      setState({ status: "ready", visit: { ...visit, guestCount }, menu });
      setGuestCountSheetOpen(false);
    } catch {
      setGuestCountError("Kaydedilemedi. Lütfen tekrar deneyin.");
    } finally {
      setGuestCountSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <VisitHeader visit={visit} onEditGuestCount={() => setGuestCountSheetOpen(true)} onOpenTracking={handleOpenTracking} />

      <div className={styles.stickyTop}>
        <SearchBar value={searchQuery} onChange={setSearchQuery} />
        <CategoryNav
          categories={isSearching ? [] : menu.categories}
          activeCategoryId={activeCategoryId}
          onSelect={scrollToCategory}
        />
      </div>

      <div className={styles.content}>
        {menu.categories.length === 0 ? (
          <EmptyState
            icon={<DishPlaceholderIcon size={32} />}
            title="Menü hazırlanıyor"
            description="Bu şube için henüz menüde ürün bulunmuyor."
          />
        ) : isSearching ? (
          searchResults.length === 0 ? (
            <EmptyState
              icon={<DishPlaceholderIcon size={32} />}
              title="Sonuç bulunamadı"
              description={`"${searchQuery.trim()}" için bir eşleşme yok.`}
            />
          ) : (
            searchResults.map((category) => (
              <MenuSection
                key={category.id}
                category={category}
                onSelectProduct={setActiveProduct}
                favoriteIds={favoriteIds}
                onToggleFavorite={toggleFavorite}
              />
            ))
          )
        ) : (
          <>
            <ProductRowSection
              title="🔥 En Çok Tercih Edilenler"
              products={popularProducts}
              onSelectProduct={setActiveProduct}
              variant="featured"
              favoriteIds={favoriteIds}
              onToggleFavorite={toggleFavorite}
              badge="Popüler"
              showSeeAll
            />
            <ProductRowSection
              title="♡ Favoriler"
              products={favoriteProducts}
              onSelectProduct={setActiveProduct}
              variant="compact"
              favoriteIds={favoriteIds}
              onToggleFavorite={toggleFavorite}
            />
            {menu.categories.map((category) => (
              <MenuSection
                key={category.id}
                category={category}
                onSelectProduct={setActiveProduct}
                favoriteIds={favoriteIds}
                onToggleFavorite={toggleFavorite}
              />
            ))}
          </>
        )}
      </div>

      {activeProduct ? (
        <ProductOptionsSheet
          product={activeProduct}
          onClose={() => {
            setActiveProduct(null);
            setCartActionError(null);
          }}
          onConfirm={handleAddToCart}
          submitting={submittingCart}
          errorMessage={cartActionError}
        />
      ) : null}

      {cart ? (
        <CartDrawer
          cart={cart}
          onRemoveItem={handleRemoveItem}
          removingItemId={removingItemId}
          onCheckout={handleCheckout}
          checkoutSubmitting={checkoutSubmitting}
          checkoutError={checkoutError}
        />
      ) : null}

      {paymentIntent ? (
        <PaymentSheet
          tableVisitId={visit.tableVisitId}
          initialIntent={paymentIntent}
          trackingToken={trackingToken}
          onClose={() => setPaymentIntent(null)}
          onOrderPaid={handleOrderPaid}
        />
      ) : null}

      {ordersSheetOpen ? (
        <OrdersSheet
          entries={orderHistoryEntries}
          onPrune={removeOrderHistoryToken}
          onClose={() => setOrdersSheetOpen(false)}
        />
      ) : null}

      {guestCountSheetOpen ? (
        <GuestCountSheet
          initialValue={visit.guestCount}
          onClose={handleGuestCountSkip}
          onConfirm={handleGuestCountConfirm}
          submitting={guestCountSubmitting}
          errorMessage={guestCountError}
        />
      ) : null}
    </main>
  );
}
