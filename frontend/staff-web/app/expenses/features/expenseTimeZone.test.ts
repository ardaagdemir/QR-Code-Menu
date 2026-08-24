import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/**
 * The regression this guards: an expense's `incurredAt` (ExpenseForm's default date) and
 * the Giderler list's default filter range (ExpenseList) must resolve "today" from the
 * active branch's own timezone (StaffContext.activeBranchTimeZone via lib/time.ts's
 * `branchIsoDate`), exactly like Özet/Kasa/Raporlar already do - never the staff device's
 * own clock (`localIsoDate`). Before this fix, ExpenseForm/ExpenseList/RecurringTemplates
 * defaulted to `localIsoDate()`, so a staff device sitting in a different timezone than the
 * branch could save/filter an expense under the wrong calendar day: the expense still showed
 * up in the (also device-local) Giderler list, but silently fell outside the branch-timezone-
 * correct Raporlar/"Yönetimsel Net Sonuç" date range - exactly the "Giderler ekranında
 * görünen giderler rapora yansımıyor" symptom.
 *
 * There is no component-render test harness in this app (see lib/time.test.ts for the
 * equivalent pure-function coverage of `branchIsoDate` itself), so this test enforces the
 * invariant at the source level: these expense-date call sites must route through
 * `branchIsoDate`, never call the device-local `localIsoDate` directly - matching the
 * explicit warning in lib/time.ts's own doc comment ("Do NOT use this for ... expense
 * dates").
 */
const FEATURES_DIR = dirname(fileURLToPath(import.meta.url));

const FILES_THAT_MUST_USE_BRANCH_TIME = ["ExpenseForm.tsx", "ExpenseList.tsx", "RecurringTemplates.tsx"];

for (const fileName of FILES_THAT_MUST_USE_BRANCH_TIME) {
  test(`${fileName} resolves expense dates via branchIsoDate, not the device-local localIsoDate`, () => {
    const source = readFileSync(join(FEATURES_DIR, fileName), "utf8");
    assert.match(source, /branchIsoDate/, `${fileName} must import/use branchIsoDate for its date defaults`);
    assert.doesNotMatch(
      source,
      /\blocalIsoDate\b/,
      `${fileName} must not call the device-local localIsoDate for branch-scoped expense dates`,
    );
  });
}
