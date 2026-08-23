/** Sipariş geçmişi backend'de müşteri oturumuna değil sadece TableVisit/token'a bağlı -
 * bu yüzden "Siparişlerim" listesi masa (tableId) bazlı localStorage'da tutulur (branch
 * bazlı OLMAZ - aynı branch'teki farklı masalar birbirinin sipariş geçmişini görmemeli,
 * bkz. kritik izolasyon bug'ı). Sadece opak orderTrackingToken değerleri (+ listede
 * "tarih/saat" gösterebilmek için token'ın bu tarayıcıya ne zaman eklendiğini belirten
 * istemci-taraflı bir zaman damgası) saklanır - ham sipariş verisi (tutar, kalemler,
 * durum) hiç yazılmaz; liste açıldığında backend'den taze getOrderTracking ile çekilir.
 * Bu, sayfa yenileme/tarayıcı kapat-aç ve "Menüye Dön" navigasyonlarında geçmişin ayakta
 * kalmasını sağlar; React state gibi component (re)mount'unda sıfırlanmaz. Aynı masanın
 * QR'ı sonradan tekrar okutulduğunda (farklı bir TableVisit başlasa bile) tableId aynı
 * kaldığı için geçmiş yeniden görünür. */

export type OrderHistoryEntry = {
  token: string;
  /** Bu token'ın bu tarayıcıya eklendiği an (ISO 8601) - yalnızca listede "tarih/saat"
   * göstermek için, sunucu/sipariş durumunun bir parçası değil. Bu alan eklenmeden önce
   * yazılmış eski kayıtlarda (veya göç edilmiş legacy verilerde) null olabilir. */
  addedAt: string | null;
};

const STORAGE_PREFIX = "qrmenu.orderHistory.";
const TABLE_STORAGE_PREFIX = `${STORAGE_PREFIX}table.`;

function storageKey(tableId: string): string {
  return `${TABLE_STORAGE_PREFIX}${tableId}`;
}

function normalizeEntry(raw: unknown): OrderHistoryEntry | null {
  // Bu alanın eklenmesinden önceki format: düz token string dizisi.
  if (typeof raw === "string") {
    return { token: raw, addedAt: null };
  }
  if (raw && typeof raw === "object" && typeof (raw as { token?: unknown }).token === "string") {
    const addedAtRaw = (raw as { addedAt?: unknown }).addedAt;
    return { token: (raw as { token: string }).token, addedAt: typeof addedAtRaw === "string" ? addedAtRaw : null };
  }
  return null;
}

// Bir önceki (branch bazlı) sürüm masadan bağımsız `qrmenu.orderHistory.<branchId>`
// anahtarı kullanıyordu - bu, aynı branch'teki tüm masaların token'larını tek listede
// karıştırıyordu (kritik izolasyon bug'ı). Hangi token'ın hangi masaya ait olduğunu
// depolanan veriden güvenle çıkaramadığımız için (aksi halde Masa 8 -> Masa 9 sızıntısını
// tekrar üretiriz), tek güvenli göç bu eski anahtarları tamamen silmektir. Token'lar
// opak olduğundan ve tek bir istekte kayıp veri oluşmadığından (backend hâlâ ilgili
// /order/track/<token> linkiyle erişilebilir) bu güvenli bir temizliktir. Her okumada
// çalışır (bir kerelik bayrak yerine) - taranan anahtar sayısı küçük, ek maliyet önemsiz.
function cleanupLegacyBranchScopedOrderHistory(): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    const legacyKeys = Object.keys(window.localStorage).filter(
      (key) => key.startsWith(STORAGE_PREFIX) && !key.startsWith(TABLE_STORAGE_PREFIX),
    );
    legacyKeys.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // best-effort - bozuk/erişilemez storage göç adımını atlamalı, okumayı engellememeli
  }
}

export function readOrderHistoryEntries(tableId: string): OrderHistoryEntry[] {
  if (typeof window === "undefined" || !tableId) {
    return [];
  }
  cleanupLegacyBranchScopedOrderHistory();
  try {
    const raw = window.localStorage.getItem(storageKey(tableId));
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(normalizeEntry).filter((entry): entry is OrderHistoryEntry => entry !== null) : [];
  } catch {
    return [];
  }
}

export function readOrderTokens(tableId: string): string[] {
  return readOrderHistoryEntries(tableId).map((entry) => entry.token);
}

function writeOrderHistoryEntries(tableId: string, entries: OrderHistoryEntry[]): void {
  if (typeof window === "undefined" || !tableId) {
    return;
  }
  window.localStorage.setItem(storageKey(tableId), JSON.stringify(entries));
}

/** Yeni bir sipariş geldikçe listeye eklenir - önceki siparişler asla ezilmez. Aynı token
 * (aynı taslak siparişe art arda eklenen ürünler nedeniyle) tekrar gelirse yeniden eklenmez. */
export function addOrderToken(tableId: string, token: string): void {
  const current = readOrderHistoryEntries(tableId);
  if (current.some((entry) => entry.token === token)) {
    return;
  }
  writeOrderHistoryEntries(tableId, [...current, { token, addedAt: new Date().toISOString() }]);
}

/** Backend'de artık bulunamayan (404) token'ların sessizce temizlenmesi için. */
export function removeOrderToken(tableId: string, token: string): void {
  const current = readOrderHistoryEntries(tableId);
  const next = current.filter((entry) => entry.token !== token);
  if (next.length !== current.length) {
    writeOrderHistoryEntries(tableId, next);
  }
}
