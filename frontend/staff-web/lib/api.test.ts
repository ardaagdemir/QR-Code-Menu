import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, isAccessDenied, isSessionExpired } from "./api";

/**
 * Regression: pages used to treat 401 (expired session) and 403 (valid session, wrong
 * role) identically and redirect both to the login page. A 403 on a route the staff
 * member isn't allowed into (e.g. CASHIER hitting /refunds) must instead surface an
 * inline "not allowed" state - see AppShell's accessDenied prop.
 */

test("isSessionExpired is true only for a 401 ApiError", () => {
  assert.equal(isSessionExpired(new ApiError("nope", 401)), true);
  assert.equal(isSessionExpired(new ApiError("nope", 403)), false);
  assert.equal(isSessionExpired(new Error("not an ApiError")), false);
  assert.equal(isSessionExpired(null), false);
});

test("isAccessDenied is true only for a 403 ApiError", () => {
  assert.equal(isAccessDenied(new ApiError("nope", 403)), true);
  assert.equal(isAccessDenied(new ApiError("nope", 401)), false);
  assert.equal(isAccessDenied(new Error("not an ApiError")), false);
  assert.equal(isAccessDenied(null), false);
});
