package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Converts the list returned by report-service into a UTF-8 CSV document. */
@Service
public class CsvReportService {

    private static final List<String> COLUMNS = List.of(
            "id", "brand", "model", "year", "vin", "price", "status", "createdAt", "soldAt"
    );
    private static final List<String> HEADERS = List.of(
            "ID", "Brand", "Model", "Year", "VIN", "Price", "Status", "Created at", "Sold at"
    );

    public byte[] generate(List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM makes Cyrillic/text display more reliably in some spreadsheet programs.
        csv.append('\uFEFF');
        appendRow(csv, HEADERS);
        for (Map<String, Object> row : rows) {
            appendRow(csv, COLUMNS.stream().map(key -> text(row.get(key))).toList());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) csv.append(',');
            csv.append(escape(values.get(index)));
        }
        csv.append("\r\n");
    }

    private String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
