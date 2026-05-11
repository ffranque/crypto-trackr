package com.cryptotrackr.repository;

import com.cryptotrackr.domain.Asset;
import com.cryptotrackr.domain.PriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    Optional<PriceRecord> findTopByAssetOrderByRecordedAtDesc(Asset asset);

    List<PriceRecord> findByAssetAndRecordedAtAfter(Asset asset, LocalDateTime from);

    Page<PriceRecord> findByAssetAndRecordedAtAfter(Asset asset, LocalDateTime from, Pageable pageable);

    Optional<PriceRecord> findTopByAssetAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
            Asset asset, LocalDateTime at);
}
