package com.example.report.service;

import com.example.report.dto.CarReportDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class CarReportService {

    private final JdbcTemplate jdbcTemplate;

    public CarReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CarReportDto> getAllCars() {

        String sql = """
                SELECT
                    id,
                    brand,
                    model,
                    year,
                    vin,
                    price,
                    status,
                    created_at,
                    sold_at
                FROM cars
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {

            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp soldAt = rs.getTimestamp("sold_at");

            return new CarReportDto(
                    rs.getLong("id"),
                    rs.getString("brand"),
                    rs.getString("model"),
                    rs.getInt("year"),
                    rs.getString("vin"),
                    rs.getBigDecimal("price"),
                    rs.getString("status"),
                    createdAt != null
                            ? createdAt.toLocalDateTime()
                            : null,
                    soldAt != null
                            ? soldAt.toLocalDateTime()
                            : null
            );
        });
    }
}