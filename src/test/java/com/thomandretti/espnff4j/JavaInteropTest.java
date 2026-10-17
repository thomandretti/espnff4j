package com.thomandretti.espnff4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thomandretti.espnff4j.model.Position;
import com.thomandretti.espnff4j.model.RosterPlayer;
import com.thomandretti.espnff4j.model.Team;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Written in Java on purpose: if the public API stops being usable from Java, this stops compiling. */
class JavaInteropTest {
    private static final String TEAMS_BODY =
        "{\"teams\":[{\"id\":1,\"name\":\"Team A\",\"playoffSeed\":1,"
            + "\"record\":{\"overall\":{\"wins\":1,\"losses\":0,\"ties\":0}}}]}";

    private final MockWebServer server = new MockWebServer();

    @BeforeEach
    void setUp() throws IOException {
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String baseUrl() {
        return server.url("/seasons/").toString();
    }

    @Test
    void futuresReturnResults() {
        server.enqueue(new MockResponse().setBody(TEAMS_BODY));
        FantasyFootballClient client = EspnFantasyFootball.client(2026, 12345, null, baseUrl());

        List<Team> teams = client.getStandingsForWeekAsync(1).join();

        assertEquals("Team A", teams.get(0).getName());
        assertEquals(1, teams.get(0).getRecord().getOverall().getWins());
    }

    @Test
    void rosterEnumsAndGettersReadNaturallyFromJava() {
        server.enqueue(new MockResponse().setBody(
            "{\"teams\":[{\"id\":1,\"name\":\"Team A\",\"playoffSeed\":1,"
                + "\"record\":{\"overall\":{\"wins\":1,\"losses\":0,\"ties\":0}},"
                + "\"roster\":{\"entries\":[{\"lineupSlotId\":0,\"playerPoolEntry\":{\"player\":"
                + "{\"id\":7,\"fullName\":\"Some QB\",\"defaultPositionId\":1,\"proTeamId\":12}}}]}}]}"));
        FantasyFootballClient client = EspnFantasyFootball.client(2026, 12345, null, baseUrl());

        RosterPlayer qb = client.getRostersForWeekAsync(6).join().get(0).getStarters().get(0);

        assertEquals(Position.QB, qb.getPosition());
        assertEquals("KC", qb.getProTeam());
        assertTrue(qb.isStarter());
    }

    @Test
    void configureTakesAPlainJavaLambda() {
        server.enqueue(new MockResponse().setBody(TEAMS_BODY));
        AtomicBoolean intercepted = new AtomicBoolean();
        FantasyFootballClient client =
            EspnFantasyFootball.client(2026, 12345, null, baseUrl(), builder -> builder.addInterceptor(chain -> {
                intercepted.set(true);
                return chain.proceed(chain.request());
            }));

        client.getStandingsForWeekAsync(1).join();

        assertTrue(intercepted.get());
    }

    @Test
    void failuresCompleteTheFutureWithAnEspnException() {
        server.enqueue(new MockResponse().setResponseCode(401));
        FantasyFootballClient client = EspnFantasyFootball.client(2026, 12345, null, baseUrl());

        CompletionException e = assertThrows(CompletionException.class, () -> client.getLeagueAsync().join());

        EspnUnauthorizedException cause = assertInstanceOf(EspnUnauthorizedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());
    }
}
