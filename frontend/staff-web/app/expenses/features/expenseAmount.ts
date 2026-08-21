const trAmountFormatter = new Intl.NumberFormat("tr-TR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Form alanında ₺ etiketi ayrıca gösterildiği için yalnız Türkçe sayı bölümünü üretir. */
export function formatAmountMinorUnitsForInput(amountMinorUnits: number): string {
  return trAmountFormatter.format(amountMinorUnits / 100);
}

/** Hem mevcut noktalı ondalık girişi hem de Türkçe 1.234,56 biçimini kabul eder. */
export function parseAmountInputToMinorUnits(value: string): number | null {
  const compact = value.trim().replaceAll(" ", "").replaceAll("₺", "");
  if (!compact) {
    return null;
  }
  const normalized = compact.includes(",")
    ? compact.replaceAll(".", "").replace(",", ".")
    : compact;
  const amount = Number(normalized);
  if (!Number.isFinite(amount)) {
    return null;
  }
  return Math.round(amount * 100);
}

export function formatAmountInput(value: string): string {
  const amountMinorUnits = parseAmountInputToMinorUnits(value);
  return amountMinorUnits === null ? value : formatAmountMinorUnitsForInput(amountMinorUnits);
}

