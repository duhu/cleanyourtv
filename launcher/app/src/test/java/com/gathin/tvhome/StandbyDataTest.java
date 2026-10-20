package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What the standby screen makes of the JSON it fetches. */
public class StandbyDataTest {

    private static final String OPEN_METEO = "{\"latitude\":31.25,\"current_units\":{\"temperature_2m\":\"°C\"},"
            + "\"current\":{\"time\":\"2026-10-20T14:15\",\"interval\":900,\"temperature_2m\":21.4,\"weather_code\":3}}";

    @Test
    public void openMeteoCurrentWeather() {
        StandbyData.WeatherInfo w = StandbyData.parseOpenMeteo(OPEN_METEO);
        assertNotNull(w);
        assertEquals(21.4, w.tempC, 1e-9);
        assertEquals(StandbyData.WeatherKind.OVERCAST, w.kind);
        assertNull(w.text);
    }

    @Test
    public void openMeteoProblemsGiveNull() {
        assertNull(StandbyData.parseOpenMeteo("not json"));
        assertNull(StandbyData.parseOpenMeteo("{\"current\":{\"weather_code\":1}}"));     // no temperature
        assertNull(StandbyData.parseOpenMeteo("{\"error\":true,\"reason\":\"bad lat\"}"));
        assertNull(StandbyData.parseOpenMeteo(null));
    }

    @Test
    public void wmoCodesMapToAFewKinds() {
        assertEquals(StandbyData.WeatherKind.CLEAR, StandbyData.weatherKind(0));
        assertEquals(StandbyData.WeatherKind.CLEAR, StandbyData.weatherKind(1));
        assertEquals(StandbyData.WeatherKind.CLOUDY, StandbyData.weatherKind(2));
        assertEquals(StandbyData.WeatherKind.OVERCAST, StandbyData.weatherKind(3));
        assertEquals(StandbyData.WeatherKind.FOG, StandbyData.weatherKind(45));
        assertEquals(StandbyData.WeatherKind.FOG, StandbyData.weatherKind(48));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(51));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(65));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(71));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(77));
        assertEquals(StandbyData.WeatherKind.RAIN, StandbyData.weatherKind(80));
        assertEquals(StandbyData.WeatherKind.SNOW, StandbyData.weatherKind(86));
        assertEquals(StandbyData.WeatherKind.THUNDER, StandbyData.weatherKind(95));
        assertEquals(StandbyData.WeatherKind.THUNDER, StandbyData.weatherKind(99));
        assertEquals(StandbyData.WeatherKind.UNKNOWN, StandbyData.weatherKind(-1));
        assertEquals(StandbyData.WeatherKind.UNKNOWN, StandbyData.weatherKind(120));
    }

    @Test
    public void gitlabLatestPipeline() {
        String json = "[{\"id\":9,\"status\":\"success\",\"ref\":\"main\",\"updated_at\":\"2026-10-20T06:12:00.123Z\"}]";
        StandbyData.CiInfo ci = StandbyData.parseGitlabPipelines("cleanyourtv", json);
        assertNotNull(ci);
        assertEquals("cleanyourtv", ci.name);
        assertEquals(StandbyData.CiStatus.SUCCESS, ci.status);
        assertEquals(StandbyData.parseIso8601("2026-10-20T06:12:00.123Z"), ci.updatedAtMillis);
    }

    @Test
    public void gitlabStatusWords() {
        for (String s : new String[]{"running", "pending", "created", "waiting_for_resource", "preparing", "scheduled"}) {
            assertEquals(s, StandbyData.CiStatus.RUNNING, pipeline(s).status);
        }
        assertEquals(StandbyData.CiStatus.FAILED, pipeline("failed").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("canceled").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("skipped").status);
        assertEquals(StandbyData.CiStatus.UNKNOWN, pipeline("manual").status);
    }

    private static StandbyData.CiInfo pipeline(String status) {
        StandbyData.CiInfo ci = StandbyData.parseGitlabPipelines("p", "[{\"status\":\"" + status + "\"}]");
        assertNotNull(ci);
        return ci;
    }

    @Test
    public void gitlabEmptyAndBroken() {
        StandbyData.CiInfo none = StandbyData.parseGitlabPipelines("p", "[]");
        assertNotNull(none);
        assertEquals(StandbyData.CiStatus.UNKNOWN, none.status);
        assertEquals(0, none.updatedAtMillis);
        assertNull(StandbyData.parseGitlabPipelines("p", "{\"message\":\"401 Unauthorized\"}"));
        assertNull(StandbyData.parseGitlabPipelines("p", "<html>"));
        assertNull(StandbyData.parseGitlabPipelines("p", null));
    }

    @Test
    public void iso8601WithZoneOrOffsetOrNothingUsable() {
        assertEquals(1_792_476_720_000L, StandbyData.parseIso8601("2026-10-20T06:12:00Z"));
        assertEquals(1_792_476_720_000L, StandbyData.parseIso8601("2026-10-20T14:12:00+08:00"));
        assertEquals(1_792_476_720_123L, StandbyData.parseIso8601("2026-10-20T06:12:00.123Z"));
        assertEquals(0, StandbyData.parseIso8601("yesterday"));
        assertEquals(0, StandbyData.parseIso8601(null));
    }

    @Test
    public void feedJsonWithEverything() {
        String json = "{\"weather\":{\"temp_c\":21,\"text\":\"多云\"},"
                + "\"ci\":[{\"name\":\"cleanyourtv\",\"status\":\"success\",\"updated_at\":\"2026-10-20T06:12:00Z\"},"
                + "{\"name\":\"api\",\"status\":\"failed\"}],"
                + "\"next_event\":{\"title\":\"需求评审\",\"start\":\"2026-10-20T06:30:00Z\"}}";
        StandbyData.StandbySnapshot s = StandbyData.parseFeed(json);
        assertNotNull(s);
        assertEquals(21.0, s.weather.tempC, 1e-9);
        assertEquals("多云", s.weather.text);
        assertEquals(StandbyData.WeatherKind.UNKNOWN, s.weather.kind);
        assertEquals(2, s.ci.size());
        assertEquals("cleanyourtv", s.ci.get(0).name);
        assertEquals(StandbyData.CiStatus.SUCCESS, s.ci.get(0).status);
        assertEquals(StandbyData.CiStatus.FAILED, s.ci.get(1).status);
        assertEquals(0, s.ci.get(1).updatedAtMillis);
    }

    @Test
    public void feedJsonWithMissingSections() {
        StandbyData.StandbySnapshot s = StandbyData.parseFeed("{}");
        assertNotNull(s);
        assertNull(s.weather);
        assertTrue(s.ci.isEmpty());
        StandbyData.StandbySnapshot bad = StandbyData.parseFeed("{\"ci\":[{\"status\":\"success\"}],\"weather\":{\"text\":\"晴\"}}");
        assertNotNull(bad);
        assertTrue(bad.ci.isEmpty());          // an entry without a name is dropped
        assertNull(bad.weather);               // weather without a temperature is dropped
        assertNull(StandbyData.parseFeed("[]"));
        assertNull(StandbyData.parseFeed(null));
    }

    @Test
    public void relativeAge() {
        long now = 1_792_476_720_000L;
        assertEquals(StandbyData.Age.JUST_NOW, StandbyData.age(now - 30_000, now).unit);
        StandbyData.Age m = StandbyData.age(now - 2 * 60_000, now);
        assertEquals(StandbyData.Age.MINUTES, m.unit);
        assertEquals(2, m.count);
        StandbyData.Age h = StandbyData.age(now - 90 * 60_000, now);
        assertEquals(StandbyData.Age.HOURS, h.unit);
        assertEquals(1, h.count);
        StandbyData.Age d = StandbyData.age(now - 3 * 24 * 3600_000L, now);
        assertEquals(StandbyData.Age.DAYS, d.unit);
        assertEquals(3, d.count);
        assertEquals(StandbyData.Age.JUST_NOW, StandbyData.age(now + 60_000, now).unit);   // clock skew: never negative
    }

    @Test
    public void projectNameIsTheLastSegment() {
        assertEquals("cleanyourtv", StandbyData.projectName("team/tv/cleanyourtv"));
        assertEquals("solo", StandbyData.projectName("solo"));
        assertEquals("x", StandbyData.projectName("a/x/"));
    }
}
