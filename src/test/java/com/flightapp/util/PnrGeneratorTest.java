package com.flightapp.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PnrGeneratorTest {

    @Test
    void generate_returnsValidPnr() {
        String pnr = PnrGenerator.generate();

        assertThat(pnr)
                .as("PNR should be non-null, upper case, non-blank, and length ≥ 6")
                .isNotNull()
                .isNotBlank()
                .isEqualTo(pnr.toUpperCase())
                .hasSizeGreaterThanOrEqualTo(6);
    }
}