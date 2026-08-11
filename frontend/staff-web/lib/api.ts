export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

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

/**
 * Every call carries the qrmenu_staff_session cookie (credentials: 'include') - the
 * real StaffUser login introduced in Milestone 8, replacing the shared
 * X-Staff-Access-Token header from Milestone 6. The cookie is HttpOnly, so the
 * frontend never reads or stores it directly; the browser attaches it automatically.
 */
async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    ...init,
    credentials: "include",
    headers: { ...(init?.body ? { "Content-Type": "application/json" } : {}), ...init?.headers },
  });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

export type StaffContext = {
  staffUserId: string;
  businessId: string;
  email: string;
  role: "PLATFORM_ADMIN" | "BUSINESS_ADMIN" | "BRANCH_MANAGER" | "CASHIER" | "KITCHEN_STAFF";
  branchIds: string[];
};

export async function login(email: string, password: string): Promise<StaffContext> {
  return apiFetch("/api/staff/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
}

export async function logout(): Promise<void> {
  await apiFetch("/api/staff/auth/logout", { method: "POST" });
}

export async function me(): Promise<StaffContext> {
  return apiFetch("/api/staff/auth/me");
}

export type KitchenOrderItemOption = {
  id: string;
  name: string;
};

export type KitchenOrderItem = {
  id: string;
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: "PENDING_REVIEW" | "PREPARING" | "REJECTED" | "READY" | "SERVED";
  options: KitchenOrderItemOption[];
};

export type KitchenOrder = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  items: KitchenOrderItem[];
};

export async function getKitchenQueue(branchId: string): Promise<KitchenOrder[]> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/orders`);
}

export async function decideOrderItem(branchId: string, orderItemId: string, acceptedQuantity: number): Promise<KitchenOrder> {
  return apiFetch(
    `/api/kitchen/branches/${encodeURIComponent(branchId)}/order-items/${encodeURIComponent(orderItemId)}/decide`,
    { method: "POST", body: JSON.stringify({ acceptedQuantity }) },
  );
}

export async function markOrderItemReady(branchId: string, orderItemId: string): Promise<KitchenOrder> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/order-items/${encodeURIComponent(orderItemId)}/ready`, {
    method: "POST",
  });
}

export async function markOrderItemServed(branchId: string, orderItemId: string): Promise<KitchenOrder> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/order-items/${encodeURIComponent(orderItemId)}/served`, {
    method: "POST",
  });
}

/** EventSource must be opened with { withCredentials: true } so the qrmenu_staff_session cookie rides along cross-origin. */
export function buildKitchenStreamUrl(branchId: string): string {
  return `${getApiBaseUrl()}/api/kitchen/branches/${encodeURIComponent(branchId)}/stream`;
}

export type StaffOrderItem = {
  id: string;
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: string;
  unitPriceMinorUnits: number;
  lineTotalMinorUnits: number;
};

export type RefundItem = {
  orderItemId: string;
  refundedQuantity: number;
  refundAmountMinorUnits: number;
};

export type Refund = {
  refundId: string;
  orderId: string;
  status: string;
  totalAmountMinorUnits: number;
  createdAt: string;
  items: RefundItem[];
};

export type StaffOrderLookup = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  items: StaffOrderItem[];
  refunds: Refund[];
};

/** Section 4, screen #6: staff finds an order by its readable order number to start a refund. */
export async function searchOrderByNumber(branchId: string, orderNumber: number): Promise<StaffOrderLookup> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/orders/search?orderNumber=${orderNumber}`);
}

export type RefundLineInput = {
  orderItemId: string;
  quantity: number;
};

/** The backend prices every line from the OrderItem's own snapshot - this never sends an amount, only quantities. */
export async function createRefund(branchId: string, orderId: string, items: RefundLineInput[]): Promise<Refund> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/orders/${encodeURIComponent(orderId)}/refunds`, {
    method: "POST",
    body: JSON.stringify({ items }),
  });
}

/** Section 4, screen #6: "teslim işlemi" - staff confirms a READY order was delivered/picked up. */
export async function completeOrder(branchId: string, orderId: string): Promise<StaffOrderLookup> {
  return apiFetch(`/api/kitchen/branches/${encodeURIComponent(branchId)}/orders/${encodeURIComponent(orderId)}/complete`, {
    method: "POST",
  });
}

// ---------------------------------------------------------------------------
// Kasa kabul/red kapısı (gap-analysis #1, product-requirements.md Section 6) -
// Permission.ORDER_VIEW/ORDER_ACCEPT/ORDER_REJECT.
// ---------------------------------------------------------------------------

export type OrderControlItem = {
  id: string;
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: string;
  options: KitchenOrderItemOption[];
};

export type OrderControlOrder = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  rejectionReasonCode: string | null;
  rejectionNote: string | null;
  items: OrderControlItem[];
};

/** Section 10.1: kasa dashboard'un "yeni ödenmiş/onay bekleyen siparişler" listesi. */
export async function getPendingAcceptanceOrders(branchId: string): Promise<OrderControlOrder[]> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/orders/pending-acceptance`);
}

export async function acceptOrder(branchId: string, orderId: string): Promise<OrderControlOrder> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/orders/${encodeURIComponent(orderId)}/accept`, {
    method: "POST",
  });
}

export async function rejectOrder(branchId: string, orderId: string, reasonCode: string, note: string): Promise<OrderControlOrder> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/orders/${encodeURIComponent(orderId)}/reject`, {
    method: "POST",
    body: JSON.stringify({ reasonCode, note: note || null }),
  });
}

export function formatPriceMinorUnits(priceMinorUnits: number): string {
  return new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency: "TRY",
  }).format(priceMinorUnits / 100);
}

// ---------------------------------------------------------------------------
// Branch / Table / QR management (Section 4, staff-web admin screens - Milestone 8)
// ---------------------------------------------------------------------------

export type DeliveryModel = "CUSTOMER_PICKUP" | "WAITER_DELIVERY";

export type Branch = {
  id: string;
  businessId: string;
  name: string;
  orderingEnabled: boolean;
  address: string | null;
  timezone: string | null;
  deliveryModel: DeliveryModel;
};

export async function listBranches(): Promise<Branch[]> {
  return apiFetch("/api/staff/branches");
}

export async function createBranch(name: string, deliveryModel: DeliveryModel): Promise<Branch> {
  return apiFetch("/api/staff/branches", { method: "POST", body: JSON.stringify({ name, deliveryModel }) });
}

export async function setOrderingEnabled(branchId: string, enabled: boolean): Promise<Branch> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/ordering-enabled`, {
    method: "POST",
    body: JSON.stringify({ enabled }),
  });
}

export async function setDeliveryModel(branchId: string, deliveryModel: DeliveryModel): Promise<Branch> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/delivery-model`, {
    method: "POST",
    body: JSON.stringify({ deliveryModel }),
  });
}

export async function setAddress(branchId: string, address: string): Promise<Branch> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/address`, {
    method: "POST",
    body: JSON.stringify({ address }),
  });
}

export async function setBranchTimezone(branchId: string, timezone: string | null): Promise<Branch> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/timezone`, {
    method: "POST",
    body: JSON.stringify({ timezone }),
  });
}

export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type BranchBusinessHoursEntry = {
  dayOfWeek: DayOfWeek;
  openingTime: string | null;
  closingTime: string | null;
  closed: boolean;
};

export async function getBusinessHours(branchId: string): Promise<BranchBusinessHoursEntry[]> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/business-hours`);
}

export async function setBusinessHours(branchId: string, days: BranchBusinessHoursEntry[]): Promise<BranchBusinessHoursEntry[]> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/business-hours`, {
    method: "POST",
    body: JSON.stringify({ days }),
  });
}

export type StaffTable = {
  id: string;
  businessId: string;
  branchId: string;
  label: string;
};

export async function listTables(branchId: string): Promise<StaffTable[]> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/tables`);
}

export async function createTable(branchId: string, label: string): Promise<StaffTable> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/tables`, {
    method: "POST",
    body: JSON.stringify({ label }),
  });
}

export type QrToken = {
  id: string;
  tableId: string;
  token: string;
  status: string;
  createdAt: string;
};

export async function getActiveQrToken(branchId: string, tableId: string): Promise<QrToken | null> {
  try {
    return await apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/tables/${encodeURIComponent(tableId)}/qr-tokens/active`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null;
    }
    throw err;
  }
}

export async function regenerateQrToken(branchId: string, tableId: string): Promise<QrToken> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/tables/${encodeURIComponent(tableId)}/qr-tokens`, {
    method: "POST",
  });
}

export async function revokeQrToken(qrTokenId: string): Promise<void> {
  await apiFetch(`/api/staff/qr-tokens/${encodeURIComponent(qrTokenId)}/revoke`, { method: "POST" });
}

// ---------------------------------------------------------------------------
// Menu management (Permission.MENU_MANAGE)
// ---------------------------------------------------------------------------

export type MenuCategoryAdmin = {
  id: string;
  businessId: string;
  name: string;
  displayOrder: number;
};

export async function listMenuCategories(): Promise<MenuCategoryAdmin[]> {
  return apiFetch("/api/staff/menu-categories");
}

export async function createMenuCategory(name: string): Promise<MenuCategoryAdmin> {
  return apiFetch("/api/staff/menu-categories", { method: "POST", body: JSON.stringify({ name }) });
}

export const ALLERGENS = [
  "GLUTEN",
  "CRUSTACEANS",
  "EGGS",
  "FISH",
  "PEANUTS",
  "SOYBEANS",
  "MILK",
  "TREE_NUTS",
  "CELERY",
  "MUSTARD",
  "SESAME",
  "SULPHITES",
  "LUPIN",
  "MOLLUSCS",
] as const;

export type Allergen = (typeof ALLERGENS)[number];

export type ProductAdmin = {
  id: string;
  businessId: string;
  categoryId: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  basePriceMinorUnits: number;
  taxRatePercent: number;
  displayOrder: number;
  active: boolean;
  estimatedPreparationMinutes: number | null;
  allergens: Allergen[];
};

export async function listProductsForCategory(categoryId: string): Promise<ProductAdmin[]> {
  return apiFetch(`/api/staff/menu-categories/${encodeURIComponent(categoryId)}/products`);
}

export async function createProduct(
  categoryId: string,
  name: string,
  basePriceMinorUnits: number,
  taxRatePercent: number,
): Promise<ProductAdmin> {
  return apiFetch("/api/staff/products", {
    method: "POST",
    body: JSON.stringify({ categoryId, name, basePriceMinorUnits, taxRatePercent }),
  });
}

export async function updateProductDetails(
  productId: string,
  active: boolean,
  estimatedPreparationMinutes: number | null,
  allergens: Allergen[],
): Promise<ProductAdmin> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}`, {
    method: "PATCH",
    body: JSON.stringify({ active, estimatedPreparationMinutes, allergens }),
  });
}

export type BranchProductAdmin = {
  id: string;
  businessId: string;
  branchId: string;
  productId: string;
  availability: "AVAILABLE" | "UNAVAILABLE";
  priceOverrideMinorUnits: number | null;
};

export async function listBranchProducts(branchId: string): Promise<BranchProductAdmin[]> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/branch-products`);
}

export async function upsertBranchProduct(
  branchId: string,
  productId: string,
  availability: "AVAILABLE" | "UNAVAILABLE",
): Promise<BranchProductAdmin> {
  return apiFetch(`/api/staff/branches/${encodeURIComponent(branchId)}/products/${encodeURIComponent(productId)}`, {
    method: "PUT",
    body: JSON.stringify({ availability, priceOverrideMinorUnits: null }),
  });
}

// ---------------------------------------------------------------------------
// Staff / role management (Permission.STAFF_MANAGE)
// ---------------------------------------------------------------------------

export type StaffRole = "BUSINESS_ADMIN" | "BRANCH_MANAGER" | "CASHIER" | "KITCHEN_STAFF";

export type StaffUser = {
  id: string;
  email: string;
  role: string;
  active: boolean;
  branchIds: string[];
  createdAt: string;
};

export async function listStaffUsers(): Promise<StaffUser[]> {
  return apiFetch("/api/staff/staff-users");
}

export async function createStaffUser(email: string, password: string, role: StaffRole, branchIds: string[]): Promise<StaffUser> {
  return apiFetch("/api/staff/staff-users", { method: "POST", body: JSON.stringify({ email, password, role, branchIds }) });
}

export async function deactivateStaffUser(staffUserId: string): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/deactivate`, { method: "POST" });
}

// ---------------------------------------------------------------------------
// Audit log (Permission.AUDIT_VIEW)
// ---------------------------------------------------------------------------

export type AuditEntry = {
  id: string;
  actorStaffUserId: string | null;
  entityType: string;
  entityId: string;
  action: string;
  details: string | null;
  createdAt: string;
};

export async function listAuditEntries(): Promise<AuditEntry[]> {
  return apiFetch("/api/staff/audit");
}

// ---------------------------------------------------------------------------
// Pickup board (Section 4, screen #8) - public/unauthenticated kiosk display,
// same posture as the customer-facing menu endpoint. No credentials involved.
// ---------------------------------------------------------------------------

export type PickupBoardEntry = {
  orderId: string;
  orderNumber: number | null;
};

export async function getPickupBoard(branchId: string): Promise<PickupBoardEntry[]> {
  const response = await fetch(`${getApiBaseUrl()}/api/branches/${encodeURIComponent(branchId)}/pickup-board`);
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  return response.json();
}

export function buildPickupBoardStreamUrl(branchId: string): string {
  return `${getApiBaseUrl()}/api/branches/${encodeURIComponent(branchId)}/pickup-board/stream`;
}

// ---------------------------------------------------------------------------
// Business settings + contacts (gap-analysis #6, Section 12.1/12.3) -
// Permission.BUSINESS_SETTINGS_MANAGE.
// ---------------------------------------------------------------------------

export type Business = {
  id: string;
  name: string;
  active: boolean;
  defaultCurrency: string;
  defaultTimeZone: string;
  createdAt: string;
};

export async function getBusiness(): Promise<Business> {
  return apiFetch("/api/staff/business");
}

export async function updateBusinessSettings(defaultCurrency: string, defaultTimeZone: string): Promise<Business> {
  return apiFetch("/api/staff/business/settings", {
    method: "POST",
    body: JSON.stringify({ defaultCurrency, defaultTimeZone }),
  });
}

export type BusinessContact = {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  whatsappEnabled: boolean;
  dailyReportRecipient: boolean;
  monthlyReportRecipient: boolean;
  active: boolean;
};

export async function listBusinessContacts(): Promise<BusinessContact[]> {
  return apiFetch("/api/staff/business/contacts");
}

export type BusinessContactInput = {
  name: string;
  phone: string;
  email: string;
  whatsappEnabled: boolean;
  dailyReportRecipient: boolean;
  monthlyReportRecipient: boolean;
};

export async function createBusinessContact(input: BusinessContactInput): Promise<BusinessContact> {
  return apiFetch("/api/staff/business/contacts", { method: "POST", body: JSON.stringify(input) });
}

export async function updateBusinessContact(
  contactId: string,
  input: BusinessContactInput & { active: boolean },
): Promise<BusinessContact> {
  return apiFetch(`/api/staff/business/contacts/${encodeURIComponent(contactId)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

// ---------------------------------------------------------------------------
// Gap-analysis #7: bulk menu assignment, staff announcements, chain comparison
// ---------------------------------------------------------------------------

export type BranchAssignmentTarget = "ALL_BRANCHES" | "SELECTED_BRANCHES";

export async function bulkAssignProductToBranches(
  productId: string,
  target: BranchAssignmentTarget,
  branchIds: string[],
): Promise<BranchProductAdmin[]> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}/branch-assignments`, {
    method: "POST",
    body: JSON.stringify({ target, branchIds }),
  });
}

export type StaffAnnouncement = {
  id: string;
  businessId: string;
  title: string;
  message: string;
  target: BranchAssignmentTarget;
  branchIds: string[];
  createdBy: string;
  createdAt: string;
  expiresAt: string | null;
};

export async function listAnnouncements(): Promise<StaffAnnouncement[]> {
  return apiFetch("/api/staff/announcements");
}

export async function listActiveAnnouncements(): Promise<StaffAnnouncement[]> {
  return apiFetch("/api/staff/announcements/active");
}

export async function createAnnouncement(
  title: string,
  message: string,
  target: BranchAssignmentTarget,
  branchIds: string[],
  expiresAt: string | null,
): Promise<StaffAnnouncement> {
  return apiFetch("/api/staff/announcements", {
    method: "POST",
    body: JSON.stringify({ title, message, target, branchIds, expiresAt }),
  });
}

export async function endAnnouncement(announcementId: string): Promise<StaffAnnouncement> {
  return apiFetch(`/api/staff/announcements/${encodeURIComponent(announcementId)}/end`, { method: "POST" });
}

export type BranchComparisonRow = {
  branchId: string;
  branchName: string;
  orderCount: number;
  tableVisitCount: number;
};

export async function getBranchComparison(): Promise<BranchComparisonRow[]> {
  return apiFetch("/api/staff/branches/comparison");
}

// ---------------------------------------------------------------------------
// Gap-analysis #8: sales reporting (Section 13) - branch report + chain view
// ---------------------------------------------------------------------------

export type ProductSalesRow = {
  productId: string;
  productName: string;
  quantitySold: number;
  revenueMinorUnits: number;
};

export type CategorySalesRow = {
  categoryId: string;
  categoryName: string;
  revenueMinorUnits: number;
};

export type HourlySalesRow = {
  hourOfDay: number;
  orderCount: number;
  revenueMinorUnits: number;
};

export type BranchSalesReport = {
  branchId: string;
  branchName: string;
  from: string;
  to: string;
  grossSalesMinorUnits: number;
  netSalesMinorUnits: number;
  refundTotalMinorUnits: number;
  orderCount: number;
  acceptedOrderCount: number;
  rejectedOrderCount: number;
  averageOrderValueMinorUnits: number;
  tableVisitCount: number;
  productBreakdown: ProductSalesRow[];
  categoryBreakdown: CategorySalesRow[];
  hourlyDistribution: HourlySalesRow[];
};

export type ChainSalesReport = {
  businessId: string;
  from: string;
  to: string;
  totalGrossSalesMinorUnits: number;
  totalNetSalesMinorUnits: number;
  totalRefundMinorUnits: number;
  totalOrderCount: number;
  branches: BranchSalesReport[];
};

export async function getBranchSalesReport(branchId: string, from: string, to: string): Promise<BranchSalesReport> {
  return apiFetch(
    `/api/staff/branches/${encodeURIComponent(branchId)}/reports?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

export async function getChainSalesReport(from: string, to: string): Promise<ChainSalesReport> {
  return apiFetch(`/api/staff/reports/chain?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}
