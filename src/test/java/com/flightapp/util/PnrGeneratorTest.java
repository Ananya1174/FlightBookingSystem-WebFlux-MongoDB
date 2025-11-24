package com.flightapp.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PnrGeneratorTest {

    @Test
    void generate_returnsNonEmptyUpper8Chars() {
        String p = PnrGenerator.generate();
        assertNotNull(p);
        assertEquals(8, p.length());
        assertTrue(p.equals(p.toUpperCase()));
    }
}