import type { CSSProperties } from "react";
import { formatPriceMinorUnits, type CategorySalesRow, type HourlySalesRow } from "@/lib/api";
import styles from "./ReportCharts.module.css";

const CHART_WIDTH = 720;
const CHART_HEIGHT = 250;
const PADDING = { top: 18, right: 18, bottom: 38, left: 58 };
const CATEGORY_COLORS = ["#e85d24", "#ef7a49", "#f39870", "#f7b79a", "#fad4c2", "#c74716"];

function compactCurrency(valueMinorUnits: number): string {
  const value = valueMinorUnits / 100;
  const numberFormat = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 1 });

  if (Math.abs(value) >= 1_000_000) {
    return `₺${numberFormat.format(value / 1_000_000)} milyon`;
  }
  if (Math.abs(value) >= 1_000) {
    return `₺${numberFormat.format(value / 1_000)} bin`;
  }
  return `₺${numberFormat.format(value)}`;
}

export function CategoryDonutChart({ items }: { items: CategorySalesRow[] }) {
  const total = Math.max(1, items.reduce((sum, item) => sum + item.revenueMinorUnits, 0));
  const percentFormat = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 1 });
  const segments = items.map((item, index) => {
    const start = items
      .slice(0, index)
      .reduce((sum, precedingItem) => sum + precedingItem.revenueMinorUnits, 0) / total * 100;
    const end = start + (item.revenueMinorUnits / total) * 100;
    return `${CATEGORY_COLORS[index % CATEGORY_COLORS.length]} ${start}% ${end}%`;
  });
  const donutStyle = { background: `conic-gradient(${segments.join(", ")})` };

  return (
    <div className={styles.donutLayout}>
      <div className={styles.donutWrap}>
        <div
          className={styles.donut}
          style={donutStyle}
          role="img"
          aria-label={items.map((item) => `${item.categoryName}: ${formatPriceMinorUnits(item.revenueMinorUnits)}`).join(", ")}
        >
          <div className={styles.donutCenter}>
            <strong>{formatPriceMinorUnits(total)}</strong>
            <span>Toplam</span>
          </div>
        </div>
      </div>
      <ul className={styles.categoryList}>
        {items.map((item, index) => (
          <li key={item.categoryId} className={styles.categoryRow}>
            <span
              className={styles.categoryDot}
              style={{ "--category-color": CATEGORY_COLORS[index % CATEGORY_COLORS.length] } as CSSProperties}
              aria-hidden="true"
            />
            <span className={styles.categoryName} title={item.categoryName}>{item.categoryName}</span>
            <span className={styles.categoryValue}>{formatPriceMinorUnits(item.revenueMinorUnits)}</span>
            <span className={styles.categoryPercent}>%{percentFormat.format((item.revenueMinorUnits / total) * 100)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** "Saatlik Satış Dağılımı" - dikey bar grafiği, saatlik ciroyu gösterir (bkz. docs/design/raporlar-ekrani.png). */
export function HourlyBarChart({ rows }: { rows: HourlySalesRow[] }) {
  const plotWidth = CHART_WIDTH - PADDING.left - PADDING.right;
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom;
  const maxValue = Math.max(1, ...rows.map((row) => row.revenueMinorUnits));
  const slotWidth = rows.length > 0 ? plotWidth / rows.length : 0;
  const barWidth = Math.max(3, slotWidth - 6);
  const labelStep = Math.max(1, Math.ceil(rows.length / 8));

  return (
    <figure className={styles.areaFigure} aria-label="Saatlik satış dağılımı bar grafiği">
      <svg className={styles.areaChart} viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} role="img">
        {[0, 0.33, 0.66, 1].map((ratio) => {
          const y = PADDING.top + plotHeight * ratio;
          const value = maxValue * (1 - ratio);
          return (
            <g key={ratio}>
              <line className={styles.gridLine} x1={PADDING.left} x2={CHART_WIDTH - PADDING.right} y1={y} y2={y} />
              <text className={styles.axisLabel} x={PADDING.left - 8} y={y + 4} textAnchor="end">{compactCurrency(value)}</text>
            </g>
          );
        })}
        {rows.map((row, index) => {
          const barHeight = (row.revenueMinorUnits / maxValue) * plotHeight;
          const x = PADDING.left + index * slotWidth + (slotWidth - barWidth) / 2;
          const y = PADDING.top + plotHeight - barHeight;
          return (
            <g key={row.hourOfDay}>
              <rect className={styles.hourBar} x={x} y={y} width={barWidth} height={Math.max(0, barHeight)} rx="2">
                <title>{`${String(row.hourOfDay).padStart(2, "0")}:00 · ${row.orderCount} sipariş · ${formatPriceMinorUnits(row.revenueMinorUnits)}`}</title>
              </rect>
              {index % labelStep === 0 || index === rows.length - 1 ? (
                <text className={styles.dateLabel} x={x + barWidth / 2} y={CHART_HEIGHT - 12} textAnchor="middle">
                  {`${String(row.hourOfDay).padStart(2, "0")}:00`}
                </text>
              ) : null}
            </g>
          );
        })}
      </svg>
      <div className={styles.chartLegend}>
        <span className={styles.legendDot} aria-hidden="true" /> Satış Tutarı (₺)
      </div>
    </figure>
  );
}
