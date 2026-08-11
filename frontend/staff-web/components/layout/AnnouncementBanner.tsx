"use client";

import { useEffect, useState } from "react";
import { listActiveAnnouncements, type StaffAnnouncement } from "@/lib/api";
import styles from "./AnnouncementBanner.module.css";

const DISMISSED_KEY = "qrmenu_dismissed_announcement_ids";

function loadDismissedIds(): Set<string> {
  if (typeof window === "undefined") {
    return new Set();
  }
  try {
    const raw = window.localStorage.getItem(DISMISSED_KEY);
    return raw ? new Set(JSON.parse(raw)) : new Set();
  } catch {
    return new Set();
  }
}

function saveDismissedIds(ids: Set<string>) {
  window.localStorage.setItem(DISMISSED_KEY, JSON.stringify(Array.from(ids)));
}

/**
 * Section 18.1 "Şube duyuruları" (gap-analysis #7): shown on every session-gated
 * staff-web page, visible to all roles (not just admins). Dismissal is browser-local
 * only (localStorage) - no server-side read-tracking, per the design's minimal scope.
 */
export default function AnnouncementBanner() {
  const [announcements, setAnnouncements] = useState<StaffAnnouncement[]>([]);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(() => loadDismissedIds());

  useEffect(() => {
    listActiveAnnouncements()
      .then(setAnnouncements)
      .catch(() => undefined);
  }, []);

  function dismiss(announcementId: string) {
    setDismissedIds((current) => {
      const next = new Set(current);
      next.add(announcementId);
      saveDismissedIds(next);
      return next;
    });
  }

  const visible = announcements.filter((announcement) => !dismissedIds.has(announcement.id));
  if (visible.length === 0) {
    return null;
  }

  return (
    <div className={styles.stack}>
      {visible.map((announcement) => (
        <div key={announcement.id} className={styles.banner}>
          <div className={styles.text}>
            <span className={styles.title}>{announcement.title}</span>
            <span className={styles.message}>{announcement.message}</span>
          </div>
          <button type="button" className={styles.dismiss} onClick={() => dismiss(announcement.id)}>
            Kapat
          </button>
        </div>
      ))}
    </div>
  );
}
