package com.example.demo.repository;

import com.example.demo.entity.ReportExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
}
