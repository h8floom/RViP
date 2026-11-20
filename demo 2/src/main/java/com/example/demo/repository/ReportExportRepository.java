package com.example.demo.repository;

import com.example.demo.entity.ReportExport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select saga from ReportExport saga where saga.id = :id")
    Optional<ReportExport> findForUpdate(@Param("id") UUID id);
}
