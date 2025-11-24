package com.flightapp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PnrGeneratorTest {

    @Test
    void generate_returnsNonEmptyUpper() {
        String p = PnrGenerator.generate();
        assertNotNull(p);
        assertFalse(p.isBlank());
        assertEquals(p.toUpperCase(), p);
        assertTrue(p.length() >= 6, "PNR should be at least 6 chars");
    }
}