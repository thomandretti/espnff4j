# espnff4j

A small Kotlin client, usable from Java, for the (unofficial, undocumented) ESPN Fantasy Football API:
league settings, standings and weekly matchups, including ESPN's live scores, projections and win
probabilities.

## Usage

```kotlin
val client = EspnFantasyFootball.client(
    seasonId = 2026,
    leagueId = 12345,
    // Private leagues only: the SWID and espn_s2 cookies from a logged-in browser session.
    credentials = EspnCredentials(swid = System.getenv("ESPN_SWID"), espnS2 = System.getenv("ESPN_S2")),
) {
    // Optional: this is an OkHttpClient.Builder, so add logging, metrics or timeouts here.
    addInterceptor(myMetricsInterceptor)
}

val league: League = client.getLeague()   // settings, plus league.currentWeek
val standings: List<Team> = client.getStandingsForWeek(week = 3)
val matchups: List<MatchupInfo> = client.getMatchupsForWeek(week = 3)
```

Weeks are NFL weeks (ESPN "scoring periods"). If a league stretches a playoff round over two weeks,
both weeks return that round's matchups.

From Java, use the `*Async` variants, which return a `CompletableFuture`:

```java
FantasyFootballClient client = EspnFantasyFootball.client(2026, 12345, credentials, EspnFantasyFootballKt.ESPN_BASE_URL,
    builder -> builder.addInterceptor(myMetricsInterceptor));   // or just client(2026, 12345)

League league = client.getLeagueAsync().join();
List<MatchupInfo> matchups = client.getMatchupsForWeekAsync(league.getCurrentWeek()).join();
```

Kotlin calls are `suspend` functions. Requests that fail with a 5xx or a connection error are retried
up to three times with a short back-off. Failures are thrown as subclasses of the sealed
`EspnException`. From Java, the future completes exceptionally with one, which `join()` wraps in a
`CompletionException`.

- `EspnUnauthorizedException` (HTTP 401/403): a private league without valid cookies. ESPN gives the
  same answer for a league ID that doesn't exist.
- `EspnNotFoundException` (HTTP 404): typically a season ESPN doesn't serve.
- `EspnApiException`: anything else, such as a persistent server error, a network failure or a
  response the client couldn't parse.

### Finding your league ID and cookies

- **League ID** — the `leagueId` query parameter in your league's URL on fantasy.espn.com.
- **Cookies** — public leagues need none. For a private league, sign in on fantasy.espn.com, open the
  browser dev tools, and copy the `SWID` and `espn_s2` cookie values. Treat them like a password:
  they grant access to your ESPN account.

## How ESPN reports scores

ESPN's payload changes shape over the course of a week, and the client normalises it:

| Field | Live (current) week | Settled week |
|---|---|---|
| `MatchupInfo.homeScore` / `awayScore` | ESPN `totalPointsLive` (`totalPoints` stays `0.0` all week) | ESPN `totalPoints` |
| `homeProjected` / `awayProjected` | `totalProjectedPointsLive`, falling back to `totalProjectedPoints` | `null` |
| `espnHomeWinProbability` | ESPN's figure for the home side | `null` |
| `outcome` | `UNDECIDED` | `HOME` / `AWAY` |

`EspnLiveResponseTest` pins this against a captured (anonymised) live payload in
`src/test/resources/espn-live-week1.json`.

## Installing

Requires Java 21. Not yet published to Maven Central. To build and use it locally:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal() }
dependencies { implementation("com.thomandretti:espnff4j:0.1.0") }
```

## Development

```bash
./gradlew ktlintFormat   # fix formatting
./gradlew check          # tests + ktlint + detekt
```

Tagging `vX.Y.Z` publishes version `X.Y.Z` from CI when the `MAVEN_REPO_URL` / `MAVEN_REPO_USER`
repository variables and `MAVEN_REPO_TOKEN` secret are set; otherwise the publish job is skipped.

## Disclaimer

Not affiliated with or endorsed by ESPN. The API is undocumented and can change without notice.

## License

[MIT](LICENSE)
