import type { CSSProperties } from "react";
import { formatPriceMinorUnits, type CategorySalesRow, type DailyCloseReport } from "@/lib/api";
import styles from "./ReportCharts.module.css";

const CHART_WIDTH = 720;
const CHART_HEIGHT = 250;
const PADDING = { top: 18, right: 18, bottom: 38, left: 58 };
const CATEGORY_COLORS = ["#e85d24", "#ef7a49", "#f39870", "#f7b79a", "#fad4c2", "#c74716"];

function friendlyDateLabel(isoDate: string): string {
  return new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short", year: "numeric" })
    .format(new Date(`${isoDate}T12:00:00`))
    .replaceAll(".", "");
}

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

export function RevenueAreaChart({ rows }: { rows: DailyCloseReport[] }) {
  const plotWidth = CHART_WIDTH - PADDING.left - PADDING.right;
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom;
  const maxValue = Math.max(1, ...rows.map((row) => row.grossSalesMinorUnits));
  const xForIndex = (index: number) => PADDING.left + (rows.length === 1 ? plotWidth / 2 : (index / (rows.length - 1)) * plotWidth);
  const yForValue = (value: number) => PADDING.top + plotHeight - (value / maxValue) * plotHeight;
  const points = rows.map((row, index) => ({
    row,
    x: xForIndex(index),
    y: yForValue(row.grossSalesMinorUnits),
  }));
  const linePath = points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
  const baseline = PADDING.top + plotHeight;
  const areaPath = points.length > 0
    ? `${linePath} L ${points.at(-1)?.x ?? PADDING.left} ${baseline} L ${points[0].x} ${baseline} Z`
    : "";
  const labelStep = Math.max(1, Math.ceil(rows.length / 7));

  return (
    <figure className={styles.areaFigure} aria-label="Günlük brüt satış çizgi grafiği">
      <svg className={styles.areaChart} viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} role="img">
        <defs>
          <linearGradient id="reportsRevenueArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--color-primary)" stopOpacity="0.3" />
            <stop offset="100%" stopColor="var(--color-primary)" stopOpacity="0.02" />
          </linearGradient>
        </defs>
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
        <path className={styles.areaFill} d={areaPath} />
        <path className={styles.line} d={linePath} />
        {points.map((point, index) => (
          <g key={point.row.businessDate}>
            <circle className={styles.pointHalo} cx={point.x} cy={point.y} r="5" />
            <circle className={styles.point} cx={point.x} cy={point.y} r="4">
              <title>{friendlyDateLabel(point.row.businessDate)} · {formatPriceMinorUnits(point.row.grossSalesMinorUnits)}</title>
            </circle>
            {(index % labelStep === 0 || index === points.length - 1) ? (
              <text
                className={styles.dateLabel}
                x={point.x}
                y={CHART_HEIGHT - 12}
                textAnchor={index === 0 ? "start" : index === points.length - 1 ? "end" : "middle"}
              >
                {friendlyDateLabel(point.row.businessDate)}
              </text>
            ) : null}
          </g>
        ))}
      </svg>
    </figure>
  );
}

export function CategoryDonutChart({ items }: { items: CategorySalesRow[] }) {
  const total = Math.max(1, items.reduce((sum, item) => sum + item.revenueMinorUnits, 0));
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
            <strong>{items.length}</strong>
            <span>Kategori</span>
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
          </li>
        ))}
      </ul>
    </div>
  );
}
