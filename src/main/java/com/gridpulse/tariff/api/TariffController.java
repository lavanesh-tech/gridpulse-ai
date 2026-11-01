package com.gridpulse.tariff.api;

import com.gridpulse.tariff.TariffBand;
import com.gridpulse.tariff.TariffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grid-connections/{gridId}/tariff")
public class TariffController {
    private final TariffService service;

    public TariffController(TariffService service) {
        this.service = service;
    }

    public record BandDto(@NotNull LocalTime start, @NotNull LocalTime end, @NotNull BigDecimal pricePerKwh) {
        TariffBand toBand() {
            return new TariffBand(start, end, pricePerKwh);
        }

        static BandDto of(TariffBand b) {
            return new BandDto(b.start(), b.end(), b.pricePerKwh());
        }
    }

    public record TariffRequest(@NotNull @Size(max = 48) List<@Valid BandDto> bands) {
    }

    @GetMapping
    public List<BandDto> get(@PathVariable UUID gridId) {
        return service.get(gridId).stream().map(BandDto::of).toList();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<BandDto> replace(@PathVariable UUID gridId, @Valid @RequestBody TariffRequest request) {
        return service.replace(gridId, request.bands().stream().map(BandDto::toBand).toList())
                .stream().map(BandDto::of).toList();
    }
}
