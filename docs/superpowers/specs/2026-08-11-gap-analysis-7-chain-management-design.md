# Gap-Analysis #7 — Toplu Menü Atama + Zincir Karşılaştırma + StaffAnnouncement

> Design spec for `docs/gap-analysis.md` §3 item #7. Scope decided via brainstorming with
> the user on 2026-08-11.

## Scope decisions

- **Branch comparison is non-financial only.** Revenue/refund comparison belongs to gap
  #8 (Reporting module), which doesn't exist yet. This item covers order count and
  TableVisit count per branch, last 24h, no date-range picker, no `guestCount` (not a
  field on `TableVisit` today — out of scope).
- **Bulk assign is availability-only.** It opts a product into `AVAILABLE` for the chosen
  branches; price overrides remain a per-branch follow-up via the existing
  `upsertBranchProduct` endpoint. No price payload in the bulk call.
- **StaffAnnouncement is a fully separate, manual feature.** It is not auto-triggered by
  bulk menu assignment — the admin composes and sends announcements independently.
- **Announcements display as a dismissible banner** on every staff-web page, visible to
  all authenticated roles (not just admins), scoped to the viewer's business/branch.

## Architecture

Three independent slices, each following existing module conventions:

1. **Bulk menu assignment** — extends the existing `menu` module (`MenuService`,
   `StaffMenuController`). No new tables.
2. **StaffAnnouncement** — new self-contained `com.qrmenu.announcement` module (entity +
   repository + service + controller), same shape as every other module.
3. **Branch comparison** — new thin `com.qrmenu.chain` module with a read-only aggregator
   service. It owns no persistence of its own; it composes existing services
   (`TenantService.listBranches`, plus one new counting method each on `OrderingService`
   and `CustomerSessionService`). Cross-module access must go through those services, per
   `ModuleBoundaryTest` — never their repositories directly.

## Data model

- New table `staff_announcement`: `id`, `business_id`, `title`, `message`, `target`
  (`ALL_BRANCHES` / `SELECTED_BRANCHES`), `created_by`, `created_at`, `expires_at`
  (nullable).
- New table `staff_announcement_branch` — element collection of branch ids, populated
  only when `target = SELECTED_BRANCHES`.
- New `Permission.ANNOUNCEMENT_MANAGE`, mapped to `BUSINESS_ADMIN` only (same admin-only
  pattern as `BUSINESS_SETTINGS_MANAGE` / `BRANCH_MANAGE`).
- No new tables for bulk assign (reuses `BranchProduct`) or comparison (reuses `Order` /
  `TableVisit`).

## Backend behavior

### Bulk menu assignment

- `MenuService.bulkAssignProductToBranches(businessId, productId, targetBranchIds, actor)`
  resolves the branch list — all branches via `TenantService.listBranches` when the
  caller asks for "all", or the given selected ids otherwise — and delegates to the
  existing `upsertBranchProduct(..., AVAILABLE, null, ...)` per branch. This reuses
  current validation (`assertBranchBelongsToBusiness`) and audit logic as-is; no new
  business logic is duplicated.
- New endpoint: `POST /api/staff/products/{productId}/branch-assignments`, body
  `{ target: "ALL_BRANCHES" | "SELECTED_BRANCHES", branchIds: UUID[] }`, gated by the
  existing `MENU_MANAGE` permission.

### StaffAnnouncement

- `AnnouncementService`:
  - `create(businessId, title, message, target, branchIds, expiresAt, actor)`
  - `listForBusiness(businessId)` — admin management list, all announcements.
  - `endNow(businessId, announcementId, actor)` — sets `expiresAt = now` if not already
    expired, so admins can retract early.
  - `listActiveFor(StaffContext context)` — filters by not-expired and
    (`target = ALL_BRANCHES` or `context.canAccessBranch(...)` for one of the target
    branches), used by the banner.
- Endpoints:
  - `GET /api/staff/announcements`, `POST /api/staff/announcements`,
    `POST /api/staff/announcements/{id}/end` — gated by `ANNOUNCEMENT_MANAGE`.
  - `GET /api/staff/announcements/active` — open to any authenticated staff member (uses
    the existing no-permission `StaffAuthService.resolveStaffContext(sessionId)`
    overload).

### Branch comparison

- `ChainComparisonService.compareBranches(businessId)` loops
  `TenantService.listBranches(businessId)`, calling two new methods:
  - `OrderingService.countOrdersSince(branchId, since)`
  - `CustomerSessionService.countTableVisitsSince(branchId, since)`

  `since` = now minus 24h, fixed window (no date-range param).
- New endpoint: `GET /api/staff/branches/comparison`, gated by `BRANCH_MANAGE` (same
  permission the existing branches list uses).

## Frontend (staff-web)

- `/menu`: add a "Şubelere ata" action per product opening an ALL/SELECTED-branches
  picker (checkboxes sourced from the existing branches endpoint).
- New `/announcements` page (admin-only nav link in `StaffNav`) to compose, list, and end
  announcements.
- New `/chain-comparison` page (admin-only nav link): a table of branches ×
  {order count, TableVisit count} for the last 24h.
- `AnnouncementBanner` component mounted once inside `StaffNav` (visible to every role),
  fetching `/api/staff/announcements/active` on load. Dismissal is per-browser via
  `localStorage` keyed by announcement id — no server-side read-tracking.

## Error handling

- Bulk assign: any selected branch not belonging to the business → the existing
  `assertBranchBelongsToBusiness` 404 path, same as today's single-branch upsert.
- Announcement: `SELECTED_BRANCHES` with an empty branch list, or `expiresAt` in the
  past, is a 400 validation error.
- Comparison: a business with zero branches returns an empty list, not an error.

## Testing

- Backend: service-level tests for `bulkAssignProductToBranches`, `AnnouncementService`,
  `ChainComparisonService`; controller tests confirming non-admin roles get 403 on the
  admin-gated endpoints; a new `ModuleBoundaryTest` case asserting
  `announcement.repository` is only reached from within the `announcement` module.
- Frontend: type-check/build only, no live browser verification (per standing user
  preference — see project memory).
