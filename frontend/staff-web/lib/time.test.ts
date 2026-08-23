import { test } from "node:test";
import assert from "node:assert/strict";
import { branchCalendarDate, branchIsoDate, localIsoDate } from "./time";

/**
 * The regression this guards: Özet/Kasa/Raporlar must all resolve "today" from the active
 * branch's own timezone (StaffContext.activeBranchTimeZone), never the staff device's own
 * clock. `branchIsoDate`/`branchCalendarDate` are what make that true; `localIsoDate` stays
 * device-local by design and must only be used as their documented pre-`me()` fallback.
 *
 * Fixed instant: 2026-08-21T22:00:00Z = 2026-08-22T01:00:00+03:00 in Europe/Istanbul - just
 * after local midnight there, while it is still "the 21st" in every device timezone at or
 * behind UTC. Same shape as the backend's own
 * ReportingFlowIntegrationTest#reportIncludesAnOrderCreatedJustAfterEuropeIstanbulMidnight...
 * regression, exercised here against the frontend's date helpers instead.
 */
const JUST_AFTER_ISTANBUL_MIDNIGHT = new Date("2026-08-21T22:00:00.000Z");

// Stand-ins for staff devices in very different timezones - none of them Europe/Istanbul.
const DEVICE_TIME_ZONES = ["UTC", "America/Los_Angeles", "Etc/GMT+12", "Pacific/Kiritimati"];

function withDeviceTimeZone<T>(timeZone: string, run: () => T): T {
  const original = process.env.TZ;
  process.env.TZ = timeZone;
  try {
    return run();
  } finally {
    if (original === undefined) {
      delete process.env.TZ;
    } else {
      process.env.TZ = original;
    }
  }
}

test("branchIsoDate resolves the branch's own calendar day regardless of the device's timezone", () => {
  for (const deviceTimeZone of DEVICE_TIME_ZONES) {
    withDeviceTimeZone(deviceTimeZone, () => {
      assert.equal(
        branchIsoDate("Europe/Istanbul", JUST_AFTER_ISTANBUL_MIDNIGHT),
        "2026-08-22",
        `device timezone ${deviceTimeZone} must not change the Europe/Istanbul branch date`,
      );
    });
  }
});

test("localIsoDate (device-local) disagrees with the branch across that same midnight boundary - why it must not drive reporting", () => {
  // Every device at/behind UTC still reads "the 21st" locally at this instant, a full
  // calendar day behind the Europe/Istanbul branch's "22nd" - the exact bug branchIsoDate
  // exists to avoid for Özet/Kasa/Raporlar.
  for (const deviceTimeZone of ["UTC", "America/Los_Angeles", "Etc/GMT+12"]) {
    withDeviceTimeZone(deviceTimeZone, () => {
      assert.equal(localIsoDate(JUST_AFTER_ISTANBUL_MIDNIGHT), "2026-08-21");
    });
  }
  // A device far enough ahead (UTC+14) happens to already agree with Istanbul here - proof
  // that device-local "today" is coincidental, not a reliable stand-in for the branch's day.
  withDeviceTimeZone("Pacific/Kiritimati", () => {
    assert.equal(localIsoDate(JUST_AFTER_ISTANBUL_MIDNIGHT), "2026-08-22");
  });
});

test("branchIsoDate also holds for a negative-offset branch, regardless of the device's timezone", () => {
  // 2026-08-22T05:00:00Z = 2026-08-21T22:00:00-07:00 in America/Los_Angeles (PDT, UTC-7) -
  // still "the 21st" there, even though a device sitting in Europe/Istanbul (UTC+3) is
  // already well into "the 22nd" locally.
  const instant = new Date("2026-08-22T05:00:00.000Z");
  for (const deviceTimeZone of ["Europe/Istanbul", "Pacific/Kiritimati", "UTC"]) {
    withDeviceTimeZone(deviceTimeZone, () => {
      assert.equal(branchIsoDate("America/Los_Angeles", instant), "2026-08-21");
    });
  }
});

test("branchIsoDate falls back to the device-local date only when no branch timezone is known yet", () => {
  withDeviceTimeZone("America/Los_Angeles", () => {
    assert.equal(branchIsoDate(null, JUST_AFTER_ISTANBUL_MIDNIGHT), localIsoDate(JUST_AFTER_ISTANBUL_MIDNIGHT));
    assert.equal(branchIsoDate(undefined, JUST_AFTER_ISTANBUL_MIDNIGHT), localIsoDate(JUST_AFTER_ISTANBUL_MIDNIGHT));
  });
});

test("branchCalendarDate carries the branch's y/m/d (not the device's) into calendar-preset arithmetic", () => {
  for (const deviceTimeZone of DEVICE_TIME_ZONES) {
    withDeviceTimeZone(deviceTimeZone, () => {
      const calendarDate = branchCalendarDate("Europe/Istanbul", JUST_AFTER_ISTANBUL_MIDNIGHT);
      // branchCalendarDate is deliberately a plain local Date built from the branch's y/m/d
      // (see lib/time.ts) - downstream preset math (DateRangePresets.presetRange) formats
      // it back out through localIsoDate, so that round-trip must reproduce the branch date.
      assert.equal(localIsoDate(calendarDate), "2026-08-22");
    });
  }
});
