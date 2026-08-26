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

/** 401: session missing/expired - caller should redirect to login. Kept distinct from
 *  isAccessDenied so a valid-session-but-wrong-role 403 doesn't bounce the user out. */
export function isSessionExpired(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401;
}

/** 403: session is valid but the role lacks permission - caller should show an inline
 *  "not allowed" state (see AppShell's accessDenied prop), not redirect to login. */
export function isAccessDenied(err: unknown): boolean {
  return err instanceof ApiError && err.status === 403;
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
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text);
}

export type StaffBranchSummary = {
  id: string;
  name: string;
};

export type StaffContext = {
  staffUserId: string;
  businessId: string;
  email: string;
  role: "PLATFORM_ADMIN" | "BUSINESS_ADMIN" | "BRANCH_MANAGER" | "CASHIER";
  branchIds: string[];
  businessName: string;
  branches: StaffBranchSummary[];
  activeBranchId: string | null;
  activeBranchName: string | null;
  /** Backend's already-resolved TenantService.resolveBranchTimeZone result - use this for
   *  "today", never the device/browser clock (see lib/time.ts's branchIsoDate). */
  activeBranchTimeZone: string | null;
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

export type OrderItemOptionSummary = {
  id: string;
  name: string;
};

export type StaffOrderItem = {
  id: string;
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: string;
  unitPriceMinorUnits: number;
  lineTotalMinorUnits: number;
  refundedQuantity: number;
  remainingRefundableQuantity: number;
};

export type RefundItem = {
  orderItemId: string;
  refundedQuantity: number;
  refundAmountMinorUnits: number;
};

export type Refund = {
  refundId: string;
  orderId: string;
  status: "REQUESTED" | "PROCESSING" | "COMPLETED" | "FAILED";
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
export async function searchOrderByNumber(orderNumber: number): Promise<StaffOrderLookup> {
  return apiFetch(`/api/staff/orders/search?orderNumber=${orderNumber}`);
}

export type RefundLineInput = {
  orderItemId: string;
  quantity: number;
};

/** The backend prices every line from the OrderItem's own snapshot - this never sends an amount, only quantities. */
export async function createRefund(orderId: string, items: RefundLineInput[]): Promise<Refund> {
  return apiFetch(`/api/staff/orders/${encodeURIComponent(orderId)}/refunds`, {
    method: "POST",
    body: JSON.stringify({ items }),
  });
}

/** Section 4, screen #6: "teslim işlemi" - staff confirms a READY order was delivered/picked up. */
export async function completeOrder(orderId: string): Promise<StaffOrderLookup> {
  return apiFetch(`/api/staff/orders/${encodeURIComponent(orderId)}/complete`, {
    method: "POST",
  });
}

// ---------------------------------------------------------------------------
// Kasa - tek operasyon ekranı (gap-analysis #1, product-requirements.md Section 6/8/11):
// kabul/red kapısı + kabul edilen siparişin PREPARING -> READY -> COMPLETED akışı, tek
// tek sipariş bazlı aksiyonlarla (item bazlı bir karar adımı yok). Permission.ORDER_VIEW
// (görüntüle) / ORDER_ACCEPT / ORDER_REJECT / ORDER_PREPARE (hazır işaretle). Ayrı bir
// Mutfak/KDS ekranı yok - bu uçlar eskiden /api/kitchen/** altındaki ayrı bir
// KitchenController'a aitti.
// ---------------------------------------------------------------------------

export type OrderControlItem = {
  id: string;
  productName: string;
  orderedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  status: "PENDING_REVIEW" | "PREPARING" | "REJECTED" | "READY" | "SERVED" | string;
  options: OrderItemOptionSummary[];
};

export type OrderControlOrder = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  rejectionReasonCode: string | null;
  rejectionNote: string | null;
  tableLabel: string | null;
  statusSince: string;
  storeAcceptanceTimeoutSeconds: number;
  items: OrderControlItem[];
};

/** Section 10.1: kasa dashboard'un "yeni ödenmiş/onay bekleyen siparişler" listesi. */
export async function getPendingAcceptanceOrders(): Promise<OrderControlOrder[]> {
  return apiFetch("/api/staff/orders/pending-acceptance");
}

export async function acceptOrder(orderId: string): Promise<OrderControlOrder> {
  return apiFetch(`/api/staff/orders/${encodeURIComponent(orderId)}/accept`, {
    method: "POST",
  });
}

export async function rejectOrder(
  orderId: string,
  reasonCode: string,
  note: string,
): Promise<OrderControlOrder> {
  return apiFetch(`/api/staff/orders/${encodeURIComponent(orderId)}/reject`, {
    method: "POST",
    body: JSON.stringify({ reasonCode, note: note || null }),
  });
}

/** Section 6/8: kabul edilmiş, hâlâ hazırlanan siparişler ("PREPARING -> READY"). */
export async function getInProgressOrders(): Promise<OrderControlOrder[]> {
  return apiFetch("/api/staff/orders/in-progress");
}

/** Section 6/8: hazır, teslim/tamamlanma bekleyen siparişler ("READY -> COMPLETED"). */
export async function getReadyOrders(): Promise<OrderControlOrder[]> {
  return apiFetch("/api/staff/orders/ready");
}

/** Section 6/8: PREPARING -> READY, the whole order at once - no item-level decision step. */
export async function markOrderReady(orderId: string): Promise<OrderControlOrder> {
  return apiFetch(`/api/staff/orders/${encodeURIComponent(orderId)}/ready`, {
    method: "POST",
  });
}

/** EventSource must be opened with { withCredentials: true } so the qrmenu_staff_session cookie rides along cross-origin. */
export function buildOrderStreamUrl(): string {
  return `${getApiBaseUrl()}/api/staff/orders/stream`;
}

// ---------------------------------------------------------------------------
// Siparişler (order history) - completed/rejected orders stay reachable after they
// drop off the Kasa board, which only ever shows active (AWAITING_STORE_ACCEPTANCE/
// IN_KITCHEN/READY) orders.
// ---------------------------------------------------------------------------

export type OrderHistoryOrder = {
  orderId: string;
  orderNumber: number | null;
  status: string;
  totalMinorUnits: number;
  rejectionReasonCode: string | null;
  rejectionNote: string | null;
  tableLabel: string | null;
  createdAt: string;
  completedAt: string | null;
  latestRefundStatus: "REQUESTED" | "PROCESSING" | "COMPLETED" | "FAILED" | null;
  items: OrderControlItem[];
};

/** Defaults to COMPLETED+REJECTED_BY_STORE when statuses is omitted - from/to are calendar dates (YYYY-MM-DD). */
export async function getOrderHistory(statuses: string[] | undefined, from: string, to: string): Promise<OrderHistoryOrder[]> {
  const statusParams = (statuses ?? []).map((status) => `status=${encodeURIComponent(status)}`).join("&");
  return apiFetch(
    `/api/staff/orders/history?${statusParams ? `${statusParams}&` : ""}from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
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
  active: boolean;
  orderingEnabled: boolean;
  openNow: boolean;
  address: string | null;
  timezone: string | null;
  deliveryModel: DeliveryModel;
  storeAcceptanceTimeoutSeconds: number;
};

export async function listBranches(): Promise<Branch[]> {
  return apiFetch("/api/staff/branch");
}

export async function setOrderingEnabled(enabled: boolean): Promise<Branch> {
  return apiFetch("/api/staff/branch/ordering-enabled", {
    method: "POST",
    body: JSON.stringify({ enabled }),
  });
}

export async function setDeliveryModel(deliveryModel: DeliveryModel): Promise<Branch> {
  return apiFetch("/api/staff/branch/delivery-model", {
    method: "POST",
    body: JSON.stringify({ deliveryModel }),
  });
}

export async function setAddress(address: string): Promise<Branch> {
  return apiFetch("/api/staff/branch/address", {
    method: "POST",
    body: JSON.stringify({ address }),
  });
}

export async function setBranchTimezone(timezone: string | null): Promise<Branch> {
  return apiFetch("/api/staff/branch/timezone", {
    method: "POST",
    body: JSON.stringify({ timezone }),
  });
}

/** Section 6/27: kasa kabul bekleme timeout'u (saniye) - branch bazında configurable. */
export async function setStoreAcceptanceTimeout(timeoutSeconds: number): Promise<Branch> {
  return apiFetch("/api/staff/branch/store-acceptance-timeout", {
    method: "POST",
    body: JSON.stringify({ timeoutSeconds }),
  });
}

export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type BranchBusinessHoursEntry = {
  dayOfWeek: DayOfWeek;
  openingTime: string | null;
  closingTime: string | null;
  closed: boolean;
};

export async function getBusinessHours(): Promise<BranchBusinessHoursEntry[]> {
  return apiFetch("/api/staff/branch/business-hours");
}

export async function setBusinessHours(days: BranchBusinessHoursEntry[]): Promise<BranchBusinessHoursEntry[]> {
  return apiFetch("/api/staff/branch/business-hours", {
    method: "POST",
    body: JSON.stringify({ days }),
  });
}

export type StaffTable = {
  id: string;
  businessId: string;
  branchId: string;
  label: string;
  active: boolean;
};

export async function listTables(): Promise<StaffTable[]> {
  return apiFetch("/api/staff/tables");
}

export async function createTable(label: string): Promise<StaffTable> {
  return apiFetch("/api/staff/tables", {
    method: "POST",
    body: JSON.stringify({ label }),
  });
}

export async function renameTable(tableId: string, label: string): Promise<StaffTable> {
  return apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}`, {
    method: "PATCH",
    body: JSON.stringify({ label }),
  });
}

/** Hard-delete: backend rejects with 409 if the table has any TableVisit history - use archiveTable instead. */
export async function deleteTable(tableId: string): Promise<void> {
  await apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}`, { method: "DELETE" });
}

/** Archive: backend rejects with 409 if the table has an active visit or order in progress. */
export async function archiveTable(tableId: string): Promise<StaffTable> {
  return apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}/archive`, { method: "POST" });
}

/** Reactivate: never auto-generates a QR - call regenerateQrToken afterwards if a working QR is needed. */
export async function reactivateTable(tableId: string): Promise<StaffTable> {
  return apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}/reactivate`, { method: "POST" });
}

export type QrToken = {
  id: string;
  tableId: string;
  token: string;
  status: string;
  createdAt: string;
};

export async function getActiveQrToken(tableId: string): Promise<QrToken | null> {
  try {
    return await apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}/qr-tokens/active`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return null;
    }
    throw err;
  }
}

export async function regenerateQrToken(tableId: string): Promise<QrToken> {
  return apiFetch(`/api/staff/tables/${encodeURIComponent(tableId)}/qr-tokens`, {
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

export async function renameMenuCategory(categoryId: string, name: string): Promise<MenuCategoryAdmin> {
  return apiFetch(`/api/staff/menu-categories/${encodeURIComponent(categoryId)}`, {
    method: "PATCH",
    body: JSON.stringify({ name }),
  });
}

/** orderedIds must be exactly the business's current category ids - the backend rejects duplicates/missing/foreign ids. */
export async function reorderMenuCategories(orderedIds: string[]): Promise<MenuCategoryAdmin[]> {
  return apiFetch("/api/staff/menu-categories/reorder", {
    method: "PATCH",
    body: JSON.stringify({ orderedIds }),
  });
}

/** 409 (ApiError.status) if the category still has products - caller should show that as an inline conflict, not a generic failure. */
export async function deleteMenuCategory(categoryId: string): Promise<void> {
  await apiFetch(`/api/staff/menu-categories/${encodeURIComponent(categoryId)}`, { method: "DELETE" });
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
  displayOrder: number;
  active: boolean;
  estimatedPreparationMinutes: number | null;
  allergens: Allergen[];
};

export async function listProductsForCategory(categoryId: string): Promise<ProductAdmin[]> {
  return apiFetch(`/api/staff/menu-categories/${encodeURIComponent(categoryId)}/products`);
}

export type CreateProductInput = {
  categoryId: string;
  name: string;
  description: string | null;
  basePriceMinorUnits: number;
  imageUrl: string | null;
  estimatedPreparationMinutes: number | null;
  allergens: Allergen[];
};

export async function createProduct(input: CreateProductInput): Promise<ProductAdmin> {
  return apiFetch("/api/staff/products", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export type UpdateProductDetailsInput = {
  name: string;
  description: string | null;
  basePriceMinorUnits: number;
  active: boolean;
  estimatedPreparationMinutes: number | null;
  allergens: Allergen[];
  imageUrl: string | null;
};

export async function updateProductDetails(productId: string, input: UpdateProductDetailsInput): Promise<ProductAdmin> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

/** orderedIds must be exactly the category's current product ids. */
export async function reorderProducts(categoryId: string, orderedIds: string[]): Promise<ProductAdmin[]> {
  return apiFetch(`/api/staff/menu-categories/${encodeURIComponent(categoryId)}/products/reorder`, {
    method: "PATCH",
    body: JSON.stringify({ orderedIds }),
  });
}

/** Hard delete - cascades the product's own option groups/options and every branch's opt-in row (backend V36). */
export async function deleteProduct(productId: string): Promise<void> {
  await apiFetch(`/api/staff/products/${encodeURIComponent(productId)}`, { method: "DELETE" });
}

export type OptionGroupAdmin = {
  id: string;
  businessId: string;
  productId: string;
  name: string;
  selectionType: "SINGLE" | "MULTIPLE";
  displayOrder: number;
};

export async function listOptionGroups(productId: string): Promise<OptionGroupAdmin[]> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}/option-groups`);
}

export async function createOptionGroup(
  productId: string,
  input: { name: string; selectionType: "SINGLE" | "MULTIPLE" },
): Promise<OptionGroupAdmin> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}/option-groups`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function updateOptionGroup(
  optionGroupId: string,
  input: { name: string; selectionType: "SINGLE" | "MULTIPLE" },
): Promise<OptionGroupAdmin> {
  return apiFetch(`/api/staff/option-groups/${encodeURIComponent(optionGroupId)}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

/** orderedIds must be exactly the product's current option-group ids. */
export async function reorderOptionGroups(productId: string, orderedIds: string[]): Promise<OptionGroupAdmin[]> {
  return apiFetch(`/api/staff/products/${encodeURIComponent(productId)}/option-groups/reorder`, {
    method: "PATCH",
    body: JSON.stringify({ orderedIds }),
  });
}

/** Hard delete - cascades this group's own options (backend V36). */
export async function deleteOptionGroup(optionGroupId: string): Promise<void> {
  await apiFetch(`/api/staff/option-groups/${encodeURIComponent(optionGroupId)}`, { method: "DELETE" });
}

export type OptionAdmin = {
  id: string;
  businessId: string;
  optionGroupId: string;
  name: string;
  priceDeltaMinorUnits: number;
  displayOrder: number;
};

export async function listOptions(optionGroupId: string): Promise<OptionAdmin[]> {
  return apiFetch(`/api/staff/option-groups/${encodeURIComponent(optionGroupId)}/options`);
}

export async function createOption(
  productId: string,
  optionGroupId: string,
  input: { name: string; priceDeltaMinorUnits: number },
): Promise<OptionAdmin> {
  return apiFetch(
    `/api/staff/products/${encodeURIComponent(productId)}/option-groups/${encodeURIComponent(optionGroupId)}/options`,
    { method: "POST", body: JSON.stringify(input) },
  );
}

export async function updateOption(optionId: string, input: { name: string; priceDeltaMinorUnits: number }): Promise<OptionAdmin> {
  return apiFetch(`/api/staff/options/${encodeURIComponent(optionId)}`, {
    method: "PATCH",
    body: JSON.stringify(input),
  });
}

/** orderedIds must be exactly the group's current option ids. */
export async function reorderOptions(optionGroupId: string, orderedIds: string[]): Promise<OptionAdmin[]> {
  return apiFetch(`/api/staff/option-groups/${encodeURIComponent(optionGroupId)}/options/reorder`, {
    method: "PATCH",
    body: JSON.stringify({ orderedIds }),
  });
}

export async function deleteOption(optionId: string): Promise<void> {
  await apiFetch(`/api/staff/options/${encodeURIComponent(optionId)}`, { method: "DELETE" });
}

/** Gap-analysis #15: upload-first, then attach the returned URL through create/update as before - no schema change. */
async function uploadMedia(path: string, file: File): Promise<string> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(`${getApiBaseUrl()}${path}`, { method: "POST", credentials: "include", body: formData });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  const body: { url: string } = await response.json();
  return body.url;
}

export async function uploadProductImage(file: File): Promise<string> {
  return uploadMedia("/api/staff/media/product-images", file);
}

export type BranchProductAdmin = {
  id: string;
  businessId: string;
  branchId: string;
  productId: string;
  availability: "AVAILABLE" | "UNAVAILABLE";
  priceOverrideMinorUnits: number | null;
};

export async function listBranchProducts(): Promise<BranchProductAdmin[]> {
  return apiFetch("/api/staff/branch-products");
}

export async function upsertBranchProduct(
  productId: string,
  availability: "AVAILABLE" | "UNAVAILABLE",
): Promise<BranchProductAdmin> {
  return apiFetch(`/api/staff/branch-products/${encodeURIComponent(productId)}`, {
    method: "PUT",
    body: JSON.stringify({ availability, priceOverrideMinorUnits: null }),
  });
}

// ---------------------------------------------------------------------------
// Staff / role management (Permission.STAFF_MANAGE)
// ---------------------------------------------------------------------------

export type StaffRole = "BUSINESS_ADMIN" | "BRANCH_MANAGER" | "CASHIER";

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

export async function createStaffUser(email: string, password: string, role: StaffRole): Promise<StaffUser> {
  return apiFetch("/api/staff/staff-users", { method: "POST", body: JSON.stringify({ email, password, role }) });
}

export async function deactivateStaffUser(staffUserId: string): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/deactivate`, { method: "POST" });
}

export async function activateStaffUser(staffUserId: string): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/activate`, { method: "POST" });
}

export async function changeStaffUserRole(staffUserId: string, role: StaffRole): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/role`, {
    method: "POST",
    body: JSON.stringify({ role }),
  });
}

/** Trimmed/lowercased and checked for uniqueness on the backend (ApiError.status 409 if taken). */
export async function updateStaffUserEmail(staffUserId: string, email: string): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/email`, {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

/** Backend's PasswordPolicy.MIN_LENGTH - kept as one constant so create/change/reset forms never drift. */
export const MIN_PASSWORD_LENGTH = 8;

/** Self-service: requires the caller's own current password. Ends every other session of theirs. */
export async function changePassword(currentPassword: string, newPassword: string, confirmNewPassword: string): Promise<void> {
  await apiFetch("/api/staff/auth/change-password", {
    method: "POST",
    body: JSON.stringify({ currentPassword, newPassword, confirmNewPassword }),
  });
}

/** Admin-triggered: sets a new temporary password the admin types themselves. Never resets your
 * own account (use changePassword for that) - the backend rejects that case with a 400. */
export async function resetStaffUserPassword(
  staffUserId: string,
  newPassword: string,
  confirmNewPassword: string,
): Promise<void> {
  await apiFetch(`/api/staff/staff-users/${encodeURIComponent(staffUserId)}/reset-password`, {
    method: "POST",
    body: JSON.stringify({ newPassword, confirmNewPassword }),
  });
}

// ---------------------------------------------------------------------------
// Audit log (Permission.AUDIT_VIEW)
// ---------------------------------------------------------------------------

export type AuditEntry = {
  id: string;
  actorStaffUserId: string | null;
  actorAccountDeleted: boolean;
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

export async function updateBusinessName(name: string): Promise<Business> {
  return apiFetch("/api/staff/business/name", {
    method: "POST",
    body: JSON.stringify({ name }),
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
  input: BusinessContactInput,
): Promise<BusinessContact> {
  return apiFetch(`/api/staff/business/contacts/${encodeURIComponent(contactId)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export async function deleteBusinessContact(contactId: string): Promise<void> {
  return apiFetch(`/api/staff/business/contacts/${encodeURIComponent(contactId)}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// Gap-analysis #7: bulk menu assignment and chain comparison
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
  guestCountTotal: number;
  guestCountRecordedVisitCount: number;
  averagePreparationSeconds: number;
  completedOrderCount: number;
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

export async function getBranchSalesReport(from: string, to: string): Promise<BranchSalesReport> {
  return apiFetch(
    `/api/staff/reports?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

export async function getChainSalesReport(from: string, to: string): Promise<ChainSalesReport> {
  return apiFetch(`/api/staff/reports/chain?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export type KitchenFinancialSummary = {
  branchId: string;
  from: string;
  to: string;
  grossSalesMinorUnits: number;
  netSalesMinorUnits: number;
  orderCount: number;
};

/** Gap-analysis #14: gated server-side by REPORT_FINANCIAL_SUMMARY_VIEW, separate from REPORT_VIEW. */
export async function getKitchenFinancialSummary(from: string, to: string): Promise<KitchenFinancialSummary> {
  return apiFetch(
    `/api/staff/reports/kitchen-summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

export type DailyCloseReport = {
  id: string;
  branchId: string;
  branchName: string;
  businessDate: string;
  periodStart: string;
  periodEnd: string;
  grossSalesMinorUnits: number;
  refundTotalMinorUnits: number;
  netSalesMinorUnits: number;
  orderCount: number;
  acceptedOrderCount: number;
  rejectedOrderCount: number;
  averageOrderValueMinorUnits: number;
  tableVisitCount: number;
  status: "PREVIEW" | "FINAL";
  generatedAt: string;
};

export async function getDailyCloseReports(from: string, to: string): Promise<DailyCloseReport[]> {
  return apiFetch(
    `/api/staff/daily-close?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

export async function generateDailyCloseFinal(businessDate: string): Promise<DailyCloseReport> {
  return apiFetch(
    `/api/staff/daily-close/final?businessDate=${encodeURIComponent(businessDate)}`,
    { method: "POST" },
  );
}

/** Excel export bypasses apiFetch (which always parses JSON) - the response body is the .xlsx binary itself. */
async function downloadFile(path: string, filename: string): Promise<void> {
  const response = await fetch(`${getApiBaseUrl()}${path}`, { credentials: "include" });
  if (!response.ok) {
    await parseErrorOrThrow(response);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export async function downloadBranchDailyCloseExcel(
  branchName: string,
  from: string,
  to: string,
): Promise<void> {
  await downloadFile(
    `/api/staff/daily-close/excel?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    `gun-sonu-${branchName}-${from}_${to}.xlsx`,
  );
}

// ---------------------------------------------------------------------------
// Gap-analysis #11 (Section 15): sahibine otomatik gün sonu bildirimi (email).
// ---------------------------------------------------------------------------

export type OwnerNotificationLog = {
  id: string;
  recipientEmail: string;
  channel: "EMAIL";
  status: "SENT" | "FAILED";
  errorMessage: string | null;
  triggeredBy: "AUTO" | "MANUAL";
  attemptedAt: string;
};

export async function getOwnerNotifications(reportId: string): Promise<OwnerNotificationLog[]> {
  return apiFetch(
    `/api/staff/daily-close/${encodeURIComponent(reportId)}/notifications`,
  );
}

export async function resendOwnerNotifications(reportId: string): Promise<OwnerNotificationLog[]> {
  return apiFetch(
    `/api/staff/daily-close/${encodeURIComponent(reportId)}/notifications/resend`,
    { method: "POST" },
  );
}

// ---------------------------------------------------------------------------
// Gap-analysis #10 (Section 16/17): gider yönetimi + "Yönetimsel Net Sonuç".
// ---------------------------------------------------------------------------

export type ExpenseCategory = {
  id: string;
  name: string;
  active: boolean;
};

export async function listExpenseCategories(): Promise<ExpenseCategory[]> {
  return apiFetch("/api/staff/expense-categories");
}

export async function createExpenseCategory(name: string): Promise<ExpenseCategory> {
  return apiFetch("/api/staff/expense-categories", { method: "POST", body: JSON.stringify({ name }) });
}

export async function updateExpenseCategory(categoryId: string, name: string): Promise<ExpenseCategory> {
  return apiFetch(`/api/staff/expense-categories/${encodeURIComponent(categoryId)}`, {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}

export async function deactivateExpenseCategory(categoryId: string): Promise<void> {
  await apiFetch(`/api/staff/expense-categories/${encodeURIComponent(categoryId)}/deactivate`, { method: "POST" });
}

export async function activateExpenseCategory(categoryId: string): Promise<void> {
  await apiFetch(`/api/staff/expense-categories/${encodeURIComponent(categoryId)}/activate`, { method: "POST" });
}

export type Expense = {
  id: string;
  branchId: string | null;
  categoryId: string;
  categoryName: string | null;
  amountMinorUnits: number;
  incurredAt: string;
  vendor: string | null;
  description: string | null;
  receiptImageUrl: string | null;
  createdAt: string;
  cancelledAt: string | null;
};

export type ExpenseInput = {
  branchId: string | null;
  categoryId: string;
  amountMinorUnits: number;
  incurredAt: string;
  vendor: string | null;
  description: string | null;
  receiptImageUrl: string | null;
};

export async function listExpenses(from: string, to: string): Promise<Expense[]> {
  return apiFetch(`/api/staff/expenses?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export async function createExpense(input: ExpenseInput): Promise<Expense> {
  return apiFetch("/api/staff/expenses", { method: "POST", body: JSON.stringify(input) });
}

/** Gap-analysis #15: allows photos or scanned documents (PDF), per Section 16.1. */
export async function uploadReceiptImage(file: File): Promise<string> {
  return uploadMedia("/api/staff/media/receipts", file);
}

export async function updateExpense(expenseId: string, input: Omit<ExpenseInput, "branchId">): Promise<Expense> {
  return apiFetch(`/api/staff/expenses/${encodeURIComponent(expenseId)}`, { method: "POST", body: JSON.stringify(input) });
}

/** Soft-void: keeps the record and its audit trail, just drops it out of report totals. */
export async function cancelExpense(expenseId: string): Promise<Expense> {
  return apiFetch(`/api/staff/expenses/${encodeURIComponent(expenseId)}/cancel`, { method: "POST" });
}

export type RecurringExpenseTemplate = {
  id: string;
  branchId: string | null;
  categoryId: string;
  categoryName: string | null;
  amountMinorUnits: number;
  vendor: string | null;
  description: string | null;
  dayOfMonth: number;
  startDate: string;
  endDate: string | null;
  active: boolean;
};

export type RecurringExpenseTemplateInput = {
  branchId: string | null;
  categoryId: string;
  amountMinorUnits: number;
  vendor: string | null;
  description: string | null;
  dayOfMonth: number;
  startDate: string;
  endDate: string | null;
};

export type RecurringExpenseTemplateUpdateInput = Omit<RecurringExpenseTemplateInput, "branchId">;

export async function listRecurringExpenseTemplates(): Promise<RecurringExpenseTemplate[]> {
  return apiFetch("/api/staff/recurring-expense-templates");
}

export async function createRecurringExpenseTemplate(input: RecurringExpenseTemplateInput): Promise<RecurringExpenseTemplate> {
  return apiFetch("/api/staff/recurring-expense-templates", { method: "POST", body: JSON.stringify(input) });
}

export async function updateRecurringExpenseTemplate(
  templateId: string,
  input: RecurringExpenseTemplateUpdateInput,
): Promise<RecurringExpenseTemplate> {
  return apiFetch(`/api/staff/recurring-expense-templates/${encodeURIComponent(templateId)}`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function deleteRecurringExpenseTemplate(templateId: string): Promise<void> {
  await apiFetch(`/api/staff/recurring-expense-templates/${encodeURIComponent(templateId)}`, { method: "DELETE" });
}

export async function deactivateRecurringExpenseTemplate(templateId: string): Promise<void> {
  await apiFetch(`/api/staff/recurring-expense-templates/${encodeURIComponent(templateId)}/deactivate`, { method: "POST" });
}

export async function activateRecurringExpenseTemplate(templateId: string): Promise<void> {
  await apiFetch(`/api/staff/recurring-expense-templates/${encodeURIComponent(templateId)}/activate`, { method: "POST" });
}

/** Section 17: kâr olarak sunulmaz - UI etiketi her zaman "Yönetimsel Net Sonuç" olmalı. */
export type OperatingResult = {
  branchId: string;
  branchName: string;
  from: string;
  to: string;
  grossSalesMinorUnits: number;
  refundTotalMinorUnits: number;
  netSalesMinorUnits: number;
  manualExpensesMinorUnits: number;
  recurringExpensesMinorUnits: number;
  totalExpensesMinorUnits: number;
  netOperatingResultMinorUnits: number;
};

export async function getOperatingResult(from: string, to: string): Promise<OperatingResult> {
  return apiFetch(
    `/api/staff/reports/operating-result?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
  );
}

// ---------------------------------------------------------------------------
// Platform Admin Panel (/api/platform-admin/**) - PLATFORM_ADMIN role only,
// session-authenticated (never /internal/**), cross-business (not scoped to the
// caller's own StaffUser.businessId). Can only assign the existing
// BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER roles - never creates or manages
// another PLATFORM_ADMIN account.
// ---------------------------------------------------------------------------

export async function listPlatformBusinesses(): Promise<Business[]> {
  return apiFetch("/api/platform-admin/businesses");
}

export async function createPlatformBusiness(name: string): Promise<Business> {
  return apiFetch("/api/platform-admin/businesses", { method: "POST", body: JSON.stringify({ name }) });
}

export async function getPlatformBusiness(businessId: string): Promise<Business> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}`);
}

export async function updatePlatformBusinessName(businessId: string, name: string): Promise<Business> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/name`, {
    method: "PUT",
    body: JSON.stringify({ name }),
  });
}

export async function activatePlatformBusiness(businessId: string): Promise<Business> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/activate`, { method: "POST" });
}

export async function deactivatePlatformBusiness(businessId: string): Promise<Business> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/deactivate`, { method: "POST" });
}

export async function listPlatformBranches(businessId: string): Promise<Branch[]> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/branches`);
}

export async function updatePlatformBranchInfo(businessId: string, branchId: string, name: string, address: string | null): Promise<Branch> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/branches/${encodeURIComponent(branchId)}`, {
    method: "PUT",
    body: JSON.stringify({ name, address }),
  });
}

export async function activatePlatformBranch(businessId: string, branchId: string): Promise<Branch> {
  return apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/branches/${encodeURIComponent(branchId)}/activate`,
    { method: "POST" },
  );
}

/** Rejected (409) if the branch still has an order in progress - see PlatformAdminBranchService. */
export async function deactivatePlatformBranch(businessId: string, branchId: string): Promise<Branch> {
  return apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/branches/${encodeURIComponent(branchId)}/deactivate`,
    { method: "POST" },
  );
}

export async function createPlatformBranch(businessId: string, name: string): Promise<Branch> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/branches`, {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}

export async function listPlatformStaffUsers(businessId: string): Promise<StaffUser[]> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users`);
}

export async function createPlatformStaffUser(
  businessId: string,
  email: string,
  password: string,
  role: StaffRole,
  branchIds: string[],
): Promise<StaffUser> {
  return apiFetch(`/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users`, {
    method: "POST",
    body: JSON.stringify({ email, password, role, branchIds }),
  });
}

export async function activatePlatformStaffUser(businessId: string, staffUserId: string): Promise<void> {
  await apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users/${encodeURIComponent(staffUserId)}/activate`,
    { method: "POST" },
  );
}

export async function deactivatePlatformStaffUser(businessId: string, staffUserId: string): Promise<void> {
  await apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users/${encodeURIComponent(staffUserId)}/deactivate`,
    { method: "POST" },
  );
}

export async function changePlatformStaffUserRole(businessId: string, staffUserId: string, role: StaffRole): Promise<void> {
  await apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users/${encodeURIComponent(staffUserId)}/role`,
    { method: "POST", body: JSON.stringify({ role }) },
  );
}

export async function resetPlatformStaffUserPassword(
  businessId: string,
  staffUserId: string,
  newPassword: string,
  confirmNewPassword: string,
): Promise<void> {
  await apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users/${encodeURIComponent(staffUserId)}/reset-password`,
    { method: "POST", body: JSON.stringify({ newPassword, confirmNewPassword }) },
  );
}

/** Irreversible - removes the StaffUser row entirely, unlike deactivatePlatformStaffUser. */
export async function hardDeletePlatformStaffUser(businessId: string, staffUserId: string): Promise<void> {
  await apiFetch(
    `/api/platform-admin/businesses/${encodeURIComponent(businessId)}/staff-users/${encodeURIComponent(staffUserId)}`,
    { method: "DELETE" },
  );
}
