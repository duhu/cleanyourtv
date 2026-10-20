package com.gathin.tvhome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The guard that keeps a failing standby feature from taking the home screen down. */
public class StandbyFeatureTest {

    private final List<Throwable> reported = new ArrayList<>();

    @Before
    public void setUp() {
        StandbyFeature.reset();
        StandbyFeature.reporter = reported::add;
    }

    @After
    public void tearDown() {
        StandbyFeature.reset();
    }

    @Test
    public void aWorkingStepRuns() {
        assertEquals("ok", StandbyFeature.run(() -> "ok", "fallback"));
        int[] ran = {0};
        StandbyFeature.run(() -> { ran[0]++; });
        assertEquals(1, ran[0]);
        assertTrue(StandbyFeature.working());
        assertTrue(reported.isEmpty());
    }

    @Test
    public void theFirstFailureSwitchesTheFeatureOff() {
        assertEquals("fallback", StandbyFeature.run(() -> {
            throw new IllegalStateException("no view");
        }, "fallback"));
        assertFalse(StandbyFeature.working());
        assertEquals(1, reported.size());
        int[] ran = {0};
        StandbyFeature.run(() -> { ran[0]++; });
        assertEquals(0, ran[0]);                 // skipped from now on
        assertEquals(1, reported.size());        // and logged only once
    }

    @Test
    public void independentOfTheCardGuard() {
        CardFeature.reset();
        // Two-argument form: a lambda that only throws fits both overloads, so name the one meant.
        StandbyFeature.run(() -> {
            throw new RuntimeException();
        }, null);
        assertTrue(CardFeature.working());
        CardFeature.reset();
    }
}
