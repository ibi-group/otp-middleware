package org.opentripplanner.middleware.auth;

import com.auth0.json.auth.TokenHolder;
import org.opentripplanner.middleware.utils.DateTimeUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Provides a wrapper around {@link TokenHolder} and computes an expiration date when the token is originally
 * fetched/constructed. We do this because the holder does not contain a way to dynamically check when the token has
 * expired (even though {@link TokenHolder#getExpiresIn()} seems like it might provide this... more on that here:
 * https://github.com/auth0/auth0-java/issues/211).
 */
public class TokenCache {
    public TokenHolder tokenHolder;
    private Date expirationDate;
    /**
     * Set expiration buffer to one minute. If the token is needed but expires in less than this time, it should be
     * trashed in favor of a new one.
     */
    public static final long EXPIRATION_BUFFER_MILLIS = 60 * 1000;

    public TokenCache(TokenHolder tokenHolder) {
        this.tokenHolder = tokenHolder;
        // Compute expiration time from current time + token duration in seconds.
        expirationDate = new Date(DateTimeUtils.currentTimeMillis() + tokenHolder.getExpiresIn() * 1000);
    }

    /**
     * @return whether the Auth0 API token is stale (i.e., it has expired or is about to expire)
     */
    public boolean isStale() {
        return millisecondsUntilExpiration() <= EXPIRATION_BUFFER_MILLIS;
    }

    public long millisecondsUntilExpiration() {
        return expirationDate.getTime() - DateTimeUtils.currentTimeMillis();
    }

    public long secondsUntilExpiration() {
        return TimeUnit.MILLISECONDS.toSeconds(millisecondsUntilExpiration());
    }

    public long minutesUntilExpiration() {
        return TimeUnit.MILLISECONDS.toMinutes(millisecondsUntilExpiration());
    }
}
