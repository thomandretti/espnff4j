# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small Kotlin/JVM library (Java 21 toolchain) wrapping the unofficial, undocumented ESPN Fantasy
Football API. Public operations: league settings and season state (`getLeague`), standings, weekly
matchups (with live scores, projections and ESPN win probabilities), weekly rosters (lineup slot,
injury status, points and projection per player), box scores (matchups plus both lineups), completed
transactions, free agents and player search.

## Commands

```bash
./gradlew check                 # tests + ktlint + detekt (what CI runs)
./gradlew test                  # tests only
./gradlew test --tests 'com.thomandretti.espnff4j.EspnLiveResponseTest'            # one class
./gradlew test --tests 'com.thomandretti.espnff4j.FantasyFootballClientTest.getStandingsForWeek*' # by method prefix
./gradlew ktlintFormat          # auto-fix formatting before ktlintCheck
./gradlew detekt
./gradlew publishToMavenLocal   # consume locally as com.thomandretti:espnff4j:<version>
```

ktlint uses `kotlin.code.style=official` with a 120-column limit (`.editorconfig`). detekt runs on its
default ruleset (no config file), so e.g. magic numbers must be named constants — see
`EspnRetryInterceptor`.

## Architecture

Request path, outermost first:

1. `EspnFantasyFootball.client(...)` (factory) builds the stack: OkHttp with an optional
   `StaticCookieJar` (injects `SWID` / `espn_s2` for private leagues), then `EspnRetryInterceptor`
   (3 attempts, linear back-off, retries 5xx and `IOException` only), then the caller's `configure`
   block, which runs *after* the library's interceptors. Retrofit + Jackson (`OBJECT_MAPPER`, unknown
   properties ignored) produce the raw interface.
2. `internal/espn/EspnFantasyFootballClient` — Retrofit interface returning `Call<T>`. Every endpoint
   hits the same URL and differs only in the `view` query params: `mSettings` (league), `mTeam`
   (standings), `mRoster,mTeam` (rosters), `mMatchupScore,mTeam,mSettings` (matchups) and
   `mMatchupScore,mScoreboard,mTeam,mSettings` (box scores), `mTransactions2,mTeam` (transactions) and
   `kona_player_info` (player lookups, see below). Request only the views a call needs, because
   `mRoster` alone is ~3 MB for a ten-team league.
3. `FantasyFootballClient` — awaits the `Call`s and normalises the raw ESPN DTOs (`internal/espn/`,
   `*Response` and friends) into the public `model/` types (`League`, `Team`, `MatchupInfo`, …).
   Standings are sorted by `playoffSeed`. Matchups and box scores share `matchupsFor(week)`, which
   drops byes (`away == null`) and joins team IDs against the `teams` list.

Teams are never deserialised straight into the public `Team`. Every response carries `EspnTeam`s plus
the league `members`, and `List<EspnTeam>.toTeams(members)` resolves owner SWIDs to `Owner`s, rounds
points for and against to the cent (ESPN sends 680.4599999999999) and builds the record splits. Only
the overall split has real points; ESPN zeroes them for home, away and division.

### Rosters and box scores

Players are mapped once, in `RosterEntry.toRosterPlayer` / `Roster?.toPlayers`, for both
`mRoster`'s `teams[].roster` and the box score's `schedule[].home/away.rosterForCurrentScoringPeriod`;
both have the same entry shape.

Each player's `stats[]` mixes seasons, weeks, actuals and projections. A week's figure is the entry
with the league's `seasonId`, `scoringPeriodId` = the week and `statSplitTypeId` 1 (a single week; 0
and 2 are season totals and averages). Within that, `statSourceId` 0 is actual points and 1 is ESPN's
projection, both as `appliedTotal`, i.e. scored with the league's settings. Actual points are absent
until a player's game starts. ESPN's numeric IDs for lineup slots, positions and NFL teams are mapped in
`internal/espn/EspnIds.kt`. Unknown IDs map to `UNKNOWN` (or a null `proTeam`) rather than failing.

`mMatchupScore` alone sends lineup entries with stats but **no player identity** (null id and name).
`mScoreboard` fills them in and roughly doubles the response, which is why box scores are a separate
call from matchups. Starters' week points sum to the side's `totalPointsLive`, which
`EspnBoxScoreResponseTest` checks.

### Transactions and player lookups

`kona_player_info` searches every player in the game and needs an `X-Fantasy-Filter` JSON header,
built by `PlayerFilters`: `filterIds`, `filterStatus` (`FREEAGENT`, `WAIVERS`), `filterSlotIds` (a
lineup slot ID, which is how ESPN filters by position) or `filterName`. `filterName` matches the start
of the full name or surname, so "josh al" and "allen" find Josh Allen but "llen" finds nothing. ESPN
rejects a `limit` without a sort (HTTP 400, "Limit request must be accompanied by a sort"). Sorting by
a week's projection is `sortAppliedStatTotal` with the value source + split + season + week, e.g.
`"1120266"`. Always send `filterStatsForCurrentSeasonScoringPeriodId`, or each player brings their
whole season.

`mTransactions2` lists everything proposed in a scoring period: lineup moves (`ROSTER`), the draft,
pending and failed waiver claims, and trade offers. `completed()` keeps only `EXECUTED` free-agent,
waiver and trade transactions. Items carry only player IDs (negative for a team defense: -16000 minus
the NFL team ID), so `getTransactionsForWeek` makes a second, `filterIds` request to name them.

A trade leaves a chain of transactions linked by `relatedTransactionId`. These are:

- the `TRADE_PROPOSAL` (`PENDING`, later `CANCELED`)
- a `TRADE_ACCEPT` with **no `status` at all**
- league votes (`TRADE_UPHOLD` / `TRADE_VETO`) or a `TRADE_DECLINE`, which are `EXECUTED` but carry no
  items

Only when the review period ends, often in the next week, does ESPN add a second `TRADE_ACCEPT`.
That one is `EXECUTED`, holds the `TRADE` items plus any `DROP` the receiving team made to fit them,
and has the proposer as `teamId`. So the only trade the library reports is an executed `TRADE_ACCEPT`.
This held for every week of this league's 2020–2026 seasons.

### Weeks vs matchup periods

The public API's `week` is an NFL week, which ESPN calls a *scoring period* (`scoringPeriodId`).
Schedule entries are keyed by *matchup period* instead. The two match one-to-one except in multi-week
playoff rounds, so `getMatchupsForWeek` converts using `settings.scheduleSettings.matchupPeriods`
(matchup period → list of scoring periods, from the `mSettings` view). If that mapping is missing it
assumes they're equal.

### Public API, from Kotlin and Java

The public surface is deliberately small: the factory, `EspnCredentials`, `FantasyFootballClient`, the
`model/` result types and the `EspnException` hierarchy. Everything else lives under
`com.thomandretti.espnff4j.internal` and is Kotlin-`internal`.

Java ignores Kotlin's `internal`, so the API is shaped for both languages, and `JavaInteropTest`
(in `src/test/java`) fails to compile if Java usability breaks:

- Each `suspend` call is `@JvmSynthetic`, so Java doesn't see it, and has a `*Async` twin returning
  `CompletableFuture` (via `kotlinx.coroutines.future`). Add both when adding an operation.
- `FantasyFootballClient` has a private constructor. The factory builds it through the companion's
  `@JvmSynthetic internal fun create`, which tests use too.
- The factory has a `@JvmSynthetic` Kotlin overload taking an `OkHttpClient.Builder.() -> Unit`
  receiver lambda, and a `@JvmStatic @JvmOverloads` one taking a `Consumer<OkHttpClient.Builder>` for
  Java (and for Kotlin calls with no lambda).

Retrofit is an `implementation` dependency; OkHttp is `api` because `configure` exposes
`OkHttpClient.Builder`, so keep its version in step with the one Retrofit pulls in.
`kotlinx-coroutines-core` is a runtime requirement (Retrofit's `await()` and the futures use it).

Every client call goes through `awaitEspn()`, which maps failures to the sealed `EspnException`:
401/403 → `EspnUnauthorizedException` (ESPN also returns 401 for a league that doesn't exist, so it
can't be told apart from a missing cookie), 404 → `EspnNotFoundException`, and everything else,
including network and Jackson errors, → `EspnApiException`.

### ESPN payload quirks (the main source of bugs)

ESPN's matchup payload changes shape between a live week and a settled week:

- Live week: `totalPoints` stays `0.0`; the real score is in `totalPointsLive`. Projections and
  `winProbability` are present; `winner` is `UNDECIDED`.
- Settled week: the `*Live`, projection and probability fields disappear; `totalPoints` holds the
  score and `winner` is `HOME`/`AWAY`.

That's why those `MatchupTeam` fields are nullable and `FantasyFootballClient` uses
`totalPointsLive ?: totalPoints` and `totalProjectedPointsLive ?: totalProjectedPoints`. Keep new
model fields nullable/defaulted unless ESPN always sends them.

## Testing notes

- Fixtures in `src/test/resources/` are trimmed, anonymised captures of real ESPN responses
  (`espn-live-week1.json` for a live `mMatchupScore` week, `espn-league-settings.json` for `mSettings`,
  `espn-rosters-week6.json` for two teams' `mRoster,mTeam` mid-week, `espn-box-scores-week6.json` for
  two of that week's matchups with `mScoreboard` lineups, `espn-teams-week6.json` for all ten teams'
  `mTeam` standings, `espn-transactions-week6.json` plus `espn-transaction-players-week6.json` for that
  week's activity and its player lookup, `espn-transactions-2024-week6.json` / `-week7.json` (plus
  `espn-transaction-players-2024-week7.json`) for a trade under review and then executed, and
  `espn-free-agents-week6.json` /
  `espn-player-search-week6.json` for `kona_player_info`).
  They pin the model against the real API shape. Refresh or add captures rather than hand-writing
  payloads when ESPN changes something. Before committing, replace the league ID, team and member
  names, logos and every SWID (the `{...}` member and owner IDs).
- `EspnFantasyFootballTest` exercises the full factory stack against `MockWebServer` (pass its URL as
  `baseUrl`).
- When mocking `Call<T>` with Mockito, stub `enqueue()` — `retrofit2.await()` resumes from the
  callback, so stubbing only `execute()` makes the test hang. The interface's default `vararg views`
  parameter needs `anyVararg()` when matching with `any()`.
- Mockito uses the inline mock maker (`src/test/resources/mockito-extensions`) so final Kotlin classes
  can be mocked.

## Release

CI (`.github/workflows/build.yml`) runs `./gradlew check` on PRs and pushes to `main`. Pushing a
`vX.Y.Z` tag publishes `X.Y.Z` (via `-Pversion`, overriding `gradle.properties`) only when the
`MAVEN_REPO_URL` / `MAVEN_REPO_USER` variables and `MAVEN_REPO_TOKEN` secret are set.
