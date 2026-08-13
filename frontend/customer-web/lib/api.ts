export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

export type TableVisit = {
  tableVisitId: string;
  businessId: string;
  branchId: string;
  tableId: string;
  businessName: string;
  branchName: string;
  tableLabel: string;
  startedAt: string;
  guestCount: number | null;
};

export type MenuOption = {
  id: string;
  name: string;
  priceDeltaMinorUnits: number;
};

export type MenuOptionGroup = {
  id: string;
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
  options: MenuOption[];
};

export const ALLERGEN_LABELS: Record<string, string> = {
  GLUTEN: "Gluten",
  CRUSTACEANS: "Kabuklu deniz ürünleri",
  EGGS: "Yumurta",
  FISH: "Balık",
  PEANUTS: "Yer fıstığı",
  SOYBEANS: "Soya",
  MILK: "Süt",
  TREE_NUTS: "Kuruyemiş",
  CELERY: "Kereviz",
  MUSTARD: "Hardal",
  SESAME: "Susam",
  SULPHITES: "Sülfit",
  LUPIN: "Acı bakla",
  MOLLUSCS: "Yumuşakçalar",
};

export type MenuProduct = {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  priceMinorUnits: number;
  taxRatePercent: number;
  availability: "AVAILABLE" | "UNAVAILABLE";
  estimatedPreparationMinutes: number | null;
  allergens: string[];
  optionGroups: MenuOptionGroup[];
};

export type MenuCategory = {
  id: string;
  name: string;
  products: MenuProduct[];
};

export type Menu = {
  branchId: string;
  categories: MenuCategory[];
};

export type CartItemOption = {
  id: string;
  name: string;
  priceDeltaMinorUnits: number;
};

export type CartItem = {
  id: string;
  productId: string;
  productName: string;
  unitPriceMinorUnits: number;
  quantity: number;
  lineTotalMinorUnits: number;
  options: CartItemOption[];
};

export type Cart = {
  tableVisitId: string;
  orderId: string | null;
  status: string | null;
  totalMinorUnits: number;
  items: CartItem[];
  orderTrackingToken: string | null;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

async function parseErrorOrThrow(response: Response): Promise<never> {
  throw new ApiError(`İstek başarısız oldu (HTTP ${response.status}).`, response.status);
}

/** Resolves the scanned QR token to a table/branch/business and starts or continues a
 * TableVisit; the backend also sets the qrmenu_session cookie on this response. */
export async function checkInWithQrToken(token: string): Promise<TableVisit> {
  const response = await fetch(`${getApiBaseUrl()}/api/qr/${encodeURIComponent(token)}/visit`, {
    method: "POST",
    credentials: "include",
  });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

/** Gap-analysis #17: records/changes/clears (guestCount: null) the real headcount for a visit. */
export async function setGuestCount(tableVisitId: string, guestCount: number | null): Promise<{ tableVisitId: string; guestCount: number | null }> {
  const response = await fetch(`${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/guest-count`, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestCount }),
  });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export async function getMenu(branchId: string): Promise<Menu> {
  const response = await fetch(`${getApiBaseUrl()}/api/branches/${encodeURIComponent(branchId)}/menu`, {
    credentials: "include",
  });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export async function getCart(tableVisitId: string): Promise<Cart> {
  const response = await fetch(`${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/cart`, {
    credentials: "include",
  });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export type AddCartItemInput = {
  productId: string;
  quantity: number;
  selectedOptionIds: string[];
};

/** The backend recomputes price from Product/BranchProduct/ProductOption itself - this
 * request deliberately carries no price, only the customer's selections. */
export async function addCartItem(tableVisitId: string, input: AddCartItemInput): Promise<Cart> {
  const response = await fetch(
    `${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/cart/items`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export async function removeCartItem(tableVisitId: string, orderItemId: string): Promise<Cart> {
  const response = await fetch(
    `${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/cart/items/${encodeURIComponent(orderItemId)}`,
    { method: "DELETE", credentials: "include" },
  );
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export type PaymentIntent = {
  paymentId: string;
  orderId: string;
  status: string;
  amountMinorUnits: number;
  provider: string;
};

export type PaymentStatusResult = {
  paymentId: string;
  orderId: string;
  paymentStatus: string;
  orderStatus: string;
};

/** Order DRAFT/PAYMENT_FAILED -> AWAITING_PAYMENT, Payment CREATED -> PROCESSING (the
 * backend also runs the authoritative branch ordering-enabled/hours check here). */
export async function createPaymentIntent(tableVisitId: string): Promise<PaymentIntent> {
  const response = await fetch(
    `${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/payments`,
    { method: "POST", credentials: "include" },
  );
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export async function getPaymentStatus(tableVisitId: string, paymentId: string): Promise<PaymentStatusResult> {
  const response = await fetch(
    `${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/payments/${encodeURIComponent(paymentId)}`,
    { credentials: "include" },
  );
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

/** The mock "hosted payment screen" trigger - this call itself never finalizes the
 * payment, it only simulates the customer completing/abandoning a real provider's
 * checkout page. The actual SUCCEEDED/FAILED transition happens moments later via a
 * separate backend webhook call; callers must poll getPaymentStatus to observe it. */
export async function triggerMockPaymentOutcome(
  tableVisitId: string,
  paymentId: string,
  outcome: "SUCCEEDED" | "FAILED",
): Promise<void> {
  const response = await fetch(
    `${getApiBaseUrl()}/api/table-visits/${encodeURIComponent(tableVisitId)}/payments/${encodeURIComponent(paymentId)}/mock-outcome`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ outcome }),
    },
  );
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
}

export type OrderTrackingItem = {
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: string;
};

export type OrderTracking = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  deliveryModel: "CUSTOMER_PICKUP" | "WAITER_DELIVERY";
  latestRefundStatus: string | null;
  items: OrderTrackingItem[];
};

/** Section 5: dar/read-only backup access via orderTrackingToken - no cookie needed. */
export async function getOrderTracking(token: string): Promise<OrderTracking> {
  const response = await fetch(`${getApiBaseUrl()}/api/order-tracking/${encodeURIComponent(token)}`);
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export function buildOrderTrackingStreamUrl(token: string): string {
  return `${getApiBaseUrl()}/api/order-tracking/${encodeURIComponent(token)}/stream`;
}

export type ReceiptItem = {
  productName: string;
  quantity: number;
  unitPriceMinorUnits: number;
  lineTotalMinorUnits: number;
};

export type ReceiptRefundItem = {
  orderItemId: string;
  refundedQuantity: number;
  refundAmountMinorUnits: number;
};

export type ReceiptRefund = {
  refundId: string;
  status: string;
  totalAmountMinorUnits: number;
  createdAt: string;
  items: ReceiptRefundItem[];
};

export type Receipt = {
  businessName: string;
  branchName: string;
  orderNumber: number | null;
  orderCreatedAt: string;
  items: ReceiptItem[];
  totalMinorUnits: number;
  totalRefundedMinorUnits: number;
  netPaidMinorUnits: number;
  refunds: ReceiptRefund[];
};

/** Section 4, screen #10: "yazdırılabilir HTML, yasal fatura değildir" - same token-only access as tracking. */
export async function getReceipt(token: string): Promise<Receipt> {
  const response = await fetch(`${getApiBaseUrl()}/api/order-tracking/${encodeURIComponent(token)}/receipt`);
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

/** priceMinorUnits is an integer amount in kuruş (Section 5) - formats it as TRY,
 * VAT-inclusive display (Section 5, "KDV/vergi ve para birimi"). */
export function formatPriceMinorUnits(priceMinorUnits: number): string {
  return new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency: "TRY",
  }).format(priceMinorUnits / 100);
}
