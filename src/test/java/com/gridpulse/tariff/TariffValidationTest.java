package com.gridpulse.tariff;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TariffValidationTest {
    private static TariffBand band(int fromH, int toH, String price) {
        return new TariffBand(LocalTime.of(fromH, 0), LocalTime.of(toH, 0), new BigDecimal(price));
    }

    @Test
    void fullDayWithAWrappingBandIsValid() {
        assertThatCode(() -> TariffService.validate(List.of(band(16, 21, "0.32"), band(21, 6, "0.11"), band(6, 16, "0.18"))))
                .doesNotThrowAnyException();
    }

    @Test
    void overlapIsRejectedIncludingAcrossMidnight() {
        assertThatThrownBy(() -> TariffService.validate(List.of(band(22, 6, "0.1"), band(5, 8, "0.2"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlap at 05:00");
    }

    @Test
    void pricesAreBoundedAndPrecisionLimited() {
        assertThatThrownBy(() -> TariffService.validate(List.of(band(0, 6, "-0.01")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TariffService.validate(List.of(band(0, 6, "10.01")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TariffService.validate(List.of(band(0, 6, "0.123456")))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> TariffService.validate(List.of(band(0, 6, "0.12345")))).doesNotThrowAnyException();
    }

    @Test
    void emptyAndSubMinuteBandsAreRejected() {
        assertThatThrownBy(() -> TariffService.validate(List.of(band(6, 6, "0.1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TariffService.validate(List.of(
                new TariffBand(LocalTime.of(1, 0, 30), LocalTime.of(2, 0), BigDecimal.ONE)))).isInstanceOf(IllegalArgumentException.class);
    }
}
