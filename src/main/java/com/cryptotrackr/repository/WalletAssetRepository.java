package com.cryptotrackr.repository;

import com.cryptotrackr.domain.Wallet;
import com.cryptotrackr.domain.WalletAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletAssetRepository extends JpaRepository<WalletAsset, Long> {

    List<WalletAsset> findByWallet(Wallet wallet);
}
