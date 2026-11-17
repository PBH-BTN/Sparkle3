package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.CurrencyExchangeRate;
import com.ghostchu.btn.sparkle.mapper.CurrencyExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeRateCacheWriter {
    private final CurrencyExchangeRateMapper mapper;

    // Cache refresh must not retain database locks in a caller's membership transaction
    // after releasing ExchangeRateService's refresh monitor.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void upsert(CurrencyExchangeRate rate) {
        mapper.upsert(rate);
    }
}
