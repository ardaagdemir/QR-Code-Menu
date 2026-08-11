package com.qrmenu.dailyclose;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * product-requirements.md Section 14.3: Excel is never the source of truth - every
 * export is built fresh from {@link DailyBranchCloseReport} rows already in the DB, on
 * request, nothing is cached or persisted as a file. A corrupted/lost workbook can never
 * lose financial data because the DB row is always there to regenerate it from.
 */
@Component
public class DailyCloseExcelExportService {

    private static final String[] HEADERS = {
        "Şube", "Tarih", "Durum", "Brüt Satış", "İade", "Net Satış", "Sipariş Sayısı",
        "Kabul Edilen", "Reddedilen", "Ortalama Sepet", "Masa Ziyareti"
    };

    public byte[] export(List<DailyBranchCloseReport> rows, Map<UUID, String> branchNamesById) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Gün Sonu Kapanışları");
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIndex = 1;
            for (DailyBranchCloseReport report : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0)
                        .setCellValue(branchNamesById.getOrDefault(report.getBranchId(), report.getBranchId().toString()));
                row.createCell(1).setCellValue(report.getBusinessDate().toString());
                row.createCell(2).setCellValue(report.getStatus().name());
                setMoney(row.createCell(3), report.getGrossSalesMinorUnits(), moneyStyle);
                setMoney(row.createCell(4), report.getRefundTotalMinorUnits(), moneyStyle);
                setMoney(row.createCell(5), report.getNetSalesMinorUnits(), moneyStyle);
                row.createCell(6).setCellValue(report.getOrderCount());
                row.createCell(7).setCellValue(report.getAcceptedOrderCount());
                row.createCell(8).setCellValue(report.getRejectedOrderCount());
                setMoney(row.createCell(9), report.getAverageOrderValueMinorUnits(), moneyStyle);
                row.createCell(10).setCellValue(report.getTableVisitCount());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Excel export başarısız oldu", e);
        }
    }

    private static void setMoney(Cell cell, long minorUnits, CellStyle style) {
        cell.setCellValue(minorUnits / 100.0);
        cell.setCellStyle(style);
    }
}
