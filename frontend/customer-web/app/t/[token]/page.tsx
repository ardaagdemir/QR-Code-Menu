"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  addCartItem,
  ApiError,
  checkInWithQrToken,
  createPaymentIntent,
  getCart,
  getMenu,
  removeCartItem,
  type Cart,
  type Menu,
  type MenuProduct,
  type PaymentIntent,
  type TableVisit,
} from "@/lib/api";
import EmptyState from "@/components/ui/EmptyState";
import ErrorState from "@/components/ui/ErrorState";
import CartDrawer from "./CartDrawer";
import CategoryNav from "./CategoryNav";
import MenuSection from "./MenuSection";
import MenuSkeleton from "./MenuSkeleton";
import PaymentSheet from "./PaymentSheet";
import ProductOptionsSheet from "./ProductOptionsSheet";
import VisitHeader from "./VisitHeader";
import styles from "./page.module.css";

type LoadState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; visit: TableVisit; menu: Menu };

function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return "Bu QR kod geçersiz ya da artık aktif değil. Lütfen masadaki QR kodu tekrar okutun.";
  }
  return "Menü yüklenirken bir sorun oluştu. İnternet bağlantınızı kontrol edip tekrar deneyin.";
}

function cartActionErrorMessage(error: unknown): string {
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
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return "Bu şube şu anda sipariş kabul etmiyor (kapalı ya da çalışma saatleri dışında).";
    }
    if (error.status === 404) {
      return "Ödemeye geçilecek bir sepet bulunamadı.";
    }
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

  const { visit, menu } = state;

  function scrollToCategory(categoryId: string) {
    setActiveCategoryId(categoryId);
    document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
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
      }
      setActiveProduct(null);
    } catch (error) {
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
    } catch {
      // Best-effort: the item stays visible in the drawer, the user can retry.
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

  return (
    <main className={styles.page}>
      <div className={styles.stickyTop}>
        <VisitHeader visit={visit} />
        <CategoryNav categories={menu.categories} activeCategoryId={activeCategoryId} onSelect={scrollToCategory} />
      </div>

      <div className={styles.content}>
        {menu.categories.length === 0 ? (
          <EmptyState icon="🍽️" title="Menü hazırlanıyor" description="Bu şube için henüz menüde ürün bulunmuyor." />
        ) : (
          menu.categories.map((category) => (
            <MenuSection key={category.id} category={category} onSelectProduct={setActiveProduct} />
          ))
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
    </main>
  );
}
