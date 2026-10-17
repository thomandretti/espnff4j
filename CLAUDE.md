# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small Kotlin/JVM library (Java 21 toolchain) wrapping the unofficial, undocumented ESPN Fantasy
Football API. Public operations: league settings and season state (`getLeague`), standings, and
weekly matchups (with live scores, projections and ESPN win probabilities).

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
2. `espn/EspnFantasyFootballClient` — Retrofit interface returning `Call<T>`. Every endpoint hits the
   same URL and differs only in the `view` query params (`mSettings`; `mRoster,mTeam`;
   `mMatchupScore,mTeam,mSettings`).
3. `FantasyFootballClient` — public `suspend` API; awaits the `Call`s and normalises ESPN's payload
   into `model/` types (`*Response` classes are the raw ESPN shape; `League`/`MatchupInfo` are the
   mapped public results). Standings are sorted by `playoffSeed`; matchups drop byes
   (`away == null`) and join team IDs against the `teams` list.

### Weeks vs matchup periods

The public API's `week` is an NFL week, which ESPN calls a *scoring period* (`scoringPeriodId`).
Schedule entries are keyed by *matchup period* instead. The two match one-to-one except in multi-week
playoff rounds, so `getMatchupsForWeek` converts using `settings.scheduleSettings.matchupPeriods`
(matchup period → list of scoring periods, from the `mSettings` view). If that mapping is missing it
assumes they're equal.

The public surface is deliberately small: the factory, `EspnCredentials`, `FantasyFootballClient` (internal
constructor), the result models (`League`, `Team`, `MatchupInfo`, …) and the `EspnException` hierarchy.
The Retrofit interface, OkHttp helpers and raw `*Response` DTOs are `internal`. Retrofit is an
`implementation` dependency; OkHttp is `api` because `configure` exposes `OkHttpClient.Builder`, so keep
its version in step with the one Retrofit pulls in.

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
  (`espn-live-week1.json` for a live `mMatchupScore` week, `espn-league-settings.json` for `mSettings`).
  They pin the model against the real API shape. Refresh or add captures rather than hand-writing
  payloads when ESPN changes something, and replace league IDs and names before committing.
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
