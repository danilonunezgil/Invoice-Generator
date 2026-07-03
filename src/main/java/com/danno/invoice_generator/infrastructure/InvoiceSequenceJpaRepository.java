package com.danno.invoice_generator.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface InvoiceSequenceJpaRepository extends JpaRepository<InvoiceSequenceEntity, Integer> {

    @Modifying
    @Query(value = "INSERT INTO invoice_sequence (fiscal_year, last_sequence) VALUES (:fiscalYear, 0) "
            + "ON CONFLICT (fiscal_year) DO NOTHING", nativeQuery = true)
    void ensureRowExists(@Param("fiscalYear") int fiscalYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequenceEntity s where s.fiscalYear = :fiscalYear")
    Optional<InvoiceSequenceEntity> findForUpdate(@Param("fiscalYear") int fiscalYear);
}
