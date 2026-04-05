package ru.nocode.recurlybilling.components.CSV;

import org.springframework.stereotype.Component;
import ru.nocode.recurlybilling.data.dto.response.AnalyticsResponse;

import java.time.format.DateTimeFormatter;

@Component
public class AnalyticsCsvExporter {

    private static final String SEP = ";";
    private static final String NL = "\r\n";
    private static final String BOM = "\uFEFF";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String exportToCsv(AnalyticsResponse analytics) {
        StringBuilder csv = new StringBuilder(BOM);

        var period = analytics.getPeriod();
        var rev = analytics.getRevenue();
        var sub = analytics.getSubscriptions();
        var cust = analytics.getCustomers();
        var pay = analytics.getPayments();

        csv.append("Показатель").append(SEP).append("Значение").append(NL);

        addRow(csv, "Период отчёта", period.getStart() + " — " + period.getEnd());
        addRow(csv, "MRR (ежемесячная выручка)", toRubles(rev.getMrr()));
        addRow(csv, "ARR (годовая выручка)", toRubles(rev.getArr()));
        addRow(csv, "Выручка за период", toRubles(rev.getTotalRevenue()));
        addRow(csv, "Средний чек", toRubles(rev.getAverageCheck()));
        addRow(csv, "Рост MRR", toPercent(rev.getGrowth()));
        addRow(csv, "Активные подписки", sub.getActive());
        addRow(csv, "Новые подписки", sub.getNewSubscriptions());
        addRow(csv, "Отменённые подписки", sub.getCanceled());
        addRow(csv, "Отток (Churn Rate)", toPercent(sub.getChurnRate()));
        addRow(csv, "Всего клиентов", cust.getTotal());
        addRow(csv, "Новые клиенты", cust.getNewCustomers());
        addRow(csv, "Активные плательщики", cust.getActivePayers());
        addRow(csv, "Удержание (Retention)", toPercent(cust.getRetentionRate()));
        addRow(csv, "Успешные платежи", pay.getSuccessful());
        addRow(csv, "Неудачные платежи", pay.getFailed());
        addRow(csv, "Возвраты", pay.getRefunded());
        addRow(csv, "Конверсия платежей", toPercent(pay.getConversionRate()));
        csv.append(NL);
        csv.append("Дата").append(SEP).append("Выручка (руб.)").append(SEP).append("Кол-во платежей").append(NL);

        if (rev.getTimeseries() != null && !rev.getTimeseries().isEmpty()) {
            for (var point : rev.getTimeseries()) {
                csv.append(point.getDate())
                        .append(SEP)
                        .append(toRubles(point.getValue()))
                        .append(NL);
            }
        } else {
            csv.append("—").append(SEP).append("0,00 ₽").append(NL);
        }

        return csv.toString();
    }

    private void addRow(StringBuilder sb, String key, Object value) {
        sb.append(escapeCsv(key)).append(SEP).append(escapeCsv(String.valueOf(value))).append(NL);
    }

    private String toRubles(Number cents) {
        if (cents == null) return "0,00 ₽";
        return String.format("%.2f ₽", cents.doubleValue() / 100.0);
    }

    private String toPercent(Double percent) {
        if (percent == null) return "0,00%";
        return String.format("%.2f%%", percent);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(SEP) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
