# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small Kotlin/JVM library (Java 21 toolchain) wrapping the unofficial, undocumented ESPN Fantasy
Football API. Public operations: league settings and season state (`getLeague`), standings, weekly
matchups (with live scores, projections and ESPN win probabilities), weekly rosters (lineup slot,
injury status, points and projection per player), box scores (matchups plus both lineups), completed
transactions and pending trades, free agents and player search, a player's week-by-week points and
news (`getPlayer`), the whole season's schedule with final standings (and so league history, via a
client per past season), the draft, and NFL game times.

## Commands

```bash
./gradlew check                 # tests + ktlint + detekt (what CI runs)
./gradlew test                  # tests only
./gradlew test --tests 'com.thomandretti.espnff4j.EspnLiveResponseTest'            # one class
./gradlew test --tests 'com.thomandretti.espnff4j.FantasyFootballClientTest.getStandingsForWeek*' # by method prefix
./gradlew ktlintFormat          # auto-fix formatting before ktlintCheck
./gradlew detekt
./gradlew publishToMavenLocal   # consume locally as com.thomandretti:espnff4j:<version>
ESPN_LEAGUE_ID=... ESPN_SWID=... ESPN_S2=... ./gradlew liveTest   # real ESPN calls; never part of check
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
2. `internal/espn/EspnFantasyFootballClient` — Retrofit interface returning `Call<T>`. Every league
   endpoint hits the same URL and differs only in the `view` query params:
   - `mSettings` (league)
   - `mTeam` (standings)
   - `mRoster,mTeam` (rosters)
   - `mMatchupScore,mTeam,mSettings` (matchups for a week; with no `scoringPeriodId`, the whole season)
   - `mMatchupScore,mScoreboard,mTeam,mSettings` (box scores)
   - `mTransactions2,mTeam` (transactions)
   - `mDraftDetail,mTeam,mSettings` (draft)
   - `kona_player_info` (player lookups, see below)

   Request only the views a call needs: `mRoster` alone is ~3 MB for a ten-team league, and `mMatchup`
   is ten times `mMatchupScore` (it adds per-stat breakdowns), so it's never used. The NFL schedule is
   the exception: `seasons/{season}?view=proTeamSchedules_wl`, not under the league and identical for
   every league.
3. `FantasyFootballClient` — awaits the `Call`s and normalises the raw ESPN DTOs (`internal/espn/`,
   `*Response` and friends) into the public `model/` types (`League`, `Team`, `MatchupInfo`, …).
   Standings are sorted by `playoffSeed`. Matchups and box scores share `matchupsFor(week)`, which
   drops byes (`away == null`) and joins team IDs against the `teams` list.

Teams are never deserialised straight into the public `Team`. Every response carries `EspnTeam`s plus
the league `members`, and `List<EspnTeam>.toTeams(members)` resolves owner SWIDs to `Owner`s, rounds
points for and against to the cent (ESPN sends 680.4599999999999) and builds the record splits. Only
the overall split has real points; ESPN zeroes them for home, away and division.
`mTeam` also carries each team's `waiverRank` and season `transactionCounter`, and `mSettings` the
league's `acquisitionSettings` (FAAB or waiver order, budget, process days). ESPN sends the FAAB budget
even in a league that doesn't use one, so `WaiverSettings` nulls it unless `isUsingAcquisitionBudget`.

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

`mMatchupScore` alone sends the requested week's lineup entries with stats but **no player identity**
(no id, name or position). `mScoreboard` fills them in and roughly doubles the response, which is why
box scores are a separate call from matchups. So `EspnPlayer`'s identity fields must stay optional, and
`toPlayers` skips anonymous entries. 0.3.0 required them, which made `getMatchupsForWeek` fail on the
live week. `EspnFantasyFootballTest` pins this with `espn-matchups-week6.json`. Starters' week points
sum to the side's `totalPointsLive`, which `EspnBoxScoreResponseTest` checks.

### Transactions and player lookups

`kona_player_info` searches every player in the game and needs an `X-Fantasy-Filter` JSON header,
built by `PlayerFilters`: `filterIds`, `filterStatus` (`FREEAGENT`, `WAIVERS`), `filterSlotIds` (a
lineup slot ID, which is how ESPN filters by position) or `filterName`. `filterName` matches the start
of the full name or surname, so "josh al" and "allen" find Josh Allen but "llen" finds nothing. ESPN
rejects a `limit` without a sort (HTTP 400, "Limit request must be accompanied by a sort"). Sorting by
a week's projection is `sortAppliedStatTotal` with the value source + split + season + week, e.g.
`"1120266"`. Always send `filterStatsForCurrentSeasonScoringPeriodId` with 0 (the season totals) plus
the weeks wanted. Leaving it out does *not* bring the whole season: with `scoringPeriodId=0`, ESPN sent
only week 1 and the season totals (checked 2026 week 6). So `getPlayer` lists weeks 1–18 explicitly
(`PlayerFilters.wholeSeason`), about 20 KB a player. ESPN then sends a projection for every week and points
for each week played. A bye, or a week the player is ruled out, has a 0.0 projection rather than none,
and a player who sat out scores 0.0.

`getPlayer`'s news is the one call not on the fantasy host: `site.api.espn.com/apis/fantasy/v2/games/ffl/
news/players?playerId=…&limit=…` (`EspnNewsClient`), public and identical for every league. It holds the
Rotowire blurbs ESPN's player card shows, newest first: injury and practice updates ("Hall (quadriceps)
is out for Sunday's game…") and game recaps. An unknown ID gets 200 with an empty `feed`. The factory
gives it its own Retrofit with no cookie jar, so account cookies never go there. A custom `baseUrl` (a
proxy or `MockWebServer`) serves it too, under `news/players`. Both of `getPlayer`'s requests run
concurrently, so tests answer them with a path-based `Dispatcher` rather than `enqueue` order.
`kona_player_info` also carries `seasonOutlook` and `outlooks.outlooksByWeek` (ESPN analysts' weekly
write-ups), which the library doesn't read yet.

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

ESPN never updates a `TRADE_PROPOSAL` after the fact: in a past week's data, an offer that was later
cancelled still says `PENDING`, and the cancellation is a second, `CANCELED` proposal whose
`relatedTransactionId` points at it. So `getPendingTrades` works out each offer from its whole chain
(`openTrades`): an offer is open unless something in the chain cancels, declines or executes it, or it
has passed its `expirationDate` (48 hours after the proposal) without being accepted. An accepted
offer stays under review past that deadline until the executed `TRADE_ACCEPT` appears, which is often
in the next week, so the call reads the requested week and the one before. Replaying every week of
2020–2025 flags exactly the two real trades, each only in the week it was under review.

### Seasons, history and the draft

The normal league endpoint serves every season the league has existed, back to 2020 for the league
these captures come from (2019 is a 404). So history is a client per past season, with no separate
`leagueHistory` endpoint. `League.previousSeasons` comes from `status.previousSeasons` in `mSettings`.
`getSchedule` is the season's `mMatchupScore` with no `scoringPeriodId`. Playoff matchups exist only
once ESPN seeds them, carry a `playoffTierType` (`WINNERS_BRACKET`, `WINNERS_CONSOLATION_LADDER`,
`LOSERS_CONSOLATION_LADDER`; `NONE` in the regular season), and a first-round bye has `away == null`.
`Team.finalRank` is `rankCalculatedFinal` (0 until the season ends); `rankFinal` stays 0, so don't use
it. `mTeam` records are always the latest, whatever `scoringPeriodId` asks for (2020 at week 1 and
week 17 are identical), so `getStandingsForWeek`'s week doesn't change standings.

`getDraft` reads `draftDetail.picks` (player IDs only) and `settings.draftSettings` (type, date), then
looks up all ~160 players in one `filterIds` request with no week. That request is the heaviest the
library makes, about 2 MB for a current season. With no week, the stats filter asks for scoring period
0, the season total: `statSplitTypeId` 0 and `statSourceId` 0, i.e. `Player.seasonPoints`. Every
player lookup also includes period 0.

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

- Fixtures in `src/test/resources/` are trimmed, anonymised captures of real ESPN responses, named
  `espn-<what>-<season or week>.json`. Each test class's KDoc says which views its fixture holds. Most
  are 2026 week 6, captured mid-week. Exceptions: `espn-live-week1.json` (week 1), the 2024 weeks 6–7
  transactions (a trade under review, then executed), and the 2025 schedule and draft (a completed
  season). They pin the model against the real API shape. Refresh or add captures rather than
  hand-writing payloads when ESPN changes something. Before committing, replace the league ID, team
  and member names, logos and every SWID (the `{...}` member and owner IDs).
- `EspnLiveSmokeTest` (tag `live`, run by `liveTest` and excluded from `test`) calls every operation against
  the real API for the current week and the last completed season. Run it before tagging a release:
  fixtures only pin the shapes ESPN sent when captured, which is how 0.3.0 shipped broken. It takes the
  league ID and cookies from the environment only; keep them out of the repo. Test methods must return
  Unit (end on an assertion, not a value), or JUnit silently skips them.
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
`MAVEN_REPO_URL` / `MAVEN_REPO_USER` variables and `MAVEN_REPO_TOKEN` secret are set. CI can't run
`liveTest`, so run it locally before tagging.
