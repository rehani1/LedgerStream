package com.ledgerstream.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class FixedWindowRateLimiter {

	private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
	private final Clock clock;

	public FixedWindowRateLimiter(Clock clock) {
		this.clock = clock;
	}

	public RateLimitResult consume(String key, int maxRequests, Duration windowSize) {
		Instant now = Instant.now(clock);
		AtomicReference<RateLimitResult> result = new AtomicReference<>();

		windows.compute(key, (ignored, current) -> {
			if (current == null || !current.expiresAt().isAfter(now)) {
				Window next = new Window(1, now.plus(windowSize));
				result.set(RateLimitResult.allowed(maxRequests - 1, next.expiresAt()));
				return next;
			}

			if (current.count() >= maxRequests) {
				result.set(RateLimitResult.rejected(Duration.between(now, current.expiresAt()), current.expiresAt()));
				return current;
			}

			Window next = new Window(current.count() + 1, current.expiresAt());
			result.set(RateLimitResult.allowed(maxRequests - next.count(), next.expiresAt()));
			return next;
		});

		return result.get();
	}

	public record RateLimitResult(
		boolean allowed,
		int remaining,
		Duration retryAfter,
		Instant resetAt
	) {

		static RateLimitResult allowed(int remaining, Instant resetAt) {
			return new RateLimitResult(true, remaining, Duration.ZERO, resetAt);
		}

		static RateLimitResult rejected(Duration retryAfter, Instant resetAt) {
			Duration safeRetryAfter = retryAfter.isNegative() || retryAfter.isZero() ? Duration.ofSeconds(1) : retryAfter;
			return new RateLimitResult(false, 0, safeRetryAfter, resetAt);
		}
	}

	private record Window(int count, Instant expiresAt) {
	}
}
