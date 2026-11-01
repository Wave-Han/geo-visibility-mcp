package io.github.wavehan.geovisibility.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MentionParserTest {

    static final List<BrandMatcher> MATCHERS = MentionParser.buildMatchers(List.of(
            new Brand("iRobot", "irobot.com", "leader", "INTL", List.of(), List.of("Roomba")),
            new Brand("Ecovacs", "ecovacs.com", "leader", "CN", List.of(), List.of("Deebot")),
            new Brand("Roborock", "roborock.com", "leader", "CN", List.of(), List.of()),
            new Brand("Litter-Robot", "litter-robot.com", "growth", "INTL", List.of(), List.of())));

    @Test
    void positionOrderByFirstAppearance() {
        var answer = "Roborock is the top pick. The Roomba j9 is classic; Ecovacs third.";
        var mentions = MentionParser.parse(answer, MATCHERS);
        assertEquals(List.of("Roborock", "iRobot", "Ecovacs"),
                mentions.stream().map(Mention::brand).toList());
        assertEquals(List.of(1, 2, 3), mentions.stream().map(Mention::position).toList());
    }

    @Test
    void aliasCountsAsCanonical() {
        var mentions = MentionParser.parse("The Roomba Combo is the best budget pick.", MATCHERS);
        assertEquals(List.of("iRobot"), mentions.stream().map(Mention::brand).toList());
    }

    @Test
    void wordBoundaryPreventsPartialMatch() {
        assertTrue(MentionParser.parse("Robotucky is different.", MATCHERS).isEmpty());
    }

    @Test
    void metacharBrandNameMatchesLiterally() {
        var mentions = MentionParser.parse("The Litter-Robot 4 is great.", MATCHERS);
        assertEquals(List.of("Litter-Robot"), mentions.stream().map(Mention::brand).toList());
    }

    @Test
    void caseInsensitive() {
        assertFalse(MentionParser.parse("ROBOROCK wins.", MATCHERS).isEmpty());
    }

    @Test
    void emptyAnswerYieldsEmpty() {
        assertTrue(MentionParser.parse("", MATCHERS).isEmpty());
    }
}
