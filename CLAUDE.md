# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A small Kotlin/JVM library (Java 21 toolchain) wrapping the unofficial, undocumented ESPN Fantasy
Football API. Two public operations: league standings and weekly matchups (with live scores,
projections and ESPN win probabilities).

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
2. `espn/EspnFantasyFootballClient` — Retrofit interface returning `Call<T>`. Both endpoints hit the
   same URL and differ only in the `view` query params (`mRoster,mTeam` vs `mMatchupScore,mTeam`).
3. `FantasyFootballClient` — public `suspend` API; awaits the `Call`s and normalises ESPN's payload
   into `model/` types. Standings are sorted by `playoffSeed`; matchups are filtered to the requested
   `matchupPeriodId`, drop byes (`away == null`), and join team IDs against the `teams` list.

`retrofit` is an `api` dependency because Retrofit and `OkHttpClient.Builder` types appear in the public
API.

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

- `EspnLiveResponseTest` deserialises a captured, anonymised live payload
  (`src/test/resources/espn-live-week1.json`) to pin the model against the real API shape. Refresh or
  add fixtures rather than hand-writing payloads when ESPN changes something.
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
