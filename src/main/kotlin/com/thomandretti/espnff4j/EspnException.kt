package com.thomandretti.espnff4j

/** Every failure [FantasyFootballClient] reports. Catch this to handle them all. */
sealed class EspnException(
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause)

/**
 * ESPN refused the request (HTTP 401 or 403). ESPN answers this way both for a private league without
 * valid `SWID` / `espn_s2` cookies and for a league ID that does not exist, so it cannot tell you which.
 */
class EspnUnauthorizedException internal constructor(
    val statusCode: Int,
    message: String,
    cause: Throwable?,
) : EspnException(message, cause)

/** HTTP 404: ESPN has no data at this path, typically a season it does not serve. */
class EspnNotFoundException internal constructor(
    message: String,
    cause: Throwable?,
) : EspnException(message, cause)

/**
 * Anything else: an HTTP error still failing after the retries, a network error, or a response the
 * client could not parse. [statusCode] is null when there was no HTTP response to report.
 */
class EspnApiException internal constructor(
    val statusCode: Int?,
    message: String,
    cause: Throwable?,
) : EspnException(message, cause)
