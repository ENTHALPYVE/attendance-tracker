package com.college.attendance.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RollNumbersTest {

    @Test
    void numbersSortByValueNotAsText() {
        List<String> rolls = new ArrayList<>(List.of("10", "2", "1", "21", "03"));
        rolls.sort(RollNumbers.NATURAL);
        assertThat(rolls).containsExactly("1", "2", "03", "10", "21");
    }

    @Test
    void prefixedRollNumbers() {
        List<String> rolls = new ArrayList<>(List.of("SE-10", "SE-9", "se-1", "TE-1"));
        rolls.sort(RollNumbers.NATURAL);
        assertThat(rolls).containsExactly("se-1", "SE-9", "SE-10", "TE-1");
    }
}
