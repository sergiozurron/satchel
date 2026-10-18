package com.jejo.satchel.middleware;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.ErrorResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRateLimitFilter extends OncePerRequestFilter {

	public static final String ERROR_RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";
	private static final String WEBHOOK_PATH = "/api/v1/funds_transfer";

	private final long capacity;
	private final long refillPeriodMs;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, IpBucket> buckets = new ConcurrentHashMap<>();

	public ApiRateLimitFilter(@Value("${satchel.api.rate-limit.capacity}") long capacity,
			@Value("${satchel.api.rate-limit.refill-period-ms}") long refillPeriodMs, ObjectMapper objectMapper) {
		this.capacity = capacity;
		this.refillPeriodMs = refillPeriodMs;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = requestPath(request);
		if (!path.startsWith("/api/")) {
			return true;
		}
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return true;
		}
		return HttpMethod.POST.matches(request.getMethod()) && WEBHOOK_PATH.equals(path);
	}

	private static String requestPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
			return path.substring(contextPath.length());
		}
		return path;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		IpBucket ipBucket = buckets.compute(request.getRemoteAddr(), (ip, existing) -> {
			if (existing == null) {
				return new IpBucket(newBucket(), System.currentTimeMillis());
			}
			existing.lastSeenMillis.set(System.currentTimeMillis());
			return existing;
		});
		ConsumptionProbe probe = ipBucket.bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			filterChain.doFilter(request, response);
			return;
		}
		long seconds = Math.max(1L, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(),
				new ErrorResponse(ERROR_RATE_LIMIT_EXCEEDED, "Too many requests"));
	}

	@Scheduled(fixedRateString = "${satchel.api.rate-limit.refill-period-ms}")
	public void dropIdleBuckets() {
		long cutoff = System.currentTimeMillis() - refillPeriodMs;
		buckets.entrySet().removeIf(entry -> entry.getValue().lastSeenMillis.get() < cutoff);
	}

	private Bucket newBucket() {
		Bandwidth limit = Bandwidth.builder().capacity(capacity)
				.refillGreedy(capacity, Duration.ofMillis(refillPeriodMs)).build();
		return Bucket.builder().addLimit(limit).build();
	}

	private static final class IpBucket {
		private final Bucket bucket;
		private final AtomicLong lastSeenMillis;

		private IpBucket(Bucket bucket, long lastSeenMillis) {
			this.bucket = bucket;
			this.lastSeenMillis = new AtomicLong(lastSeenMillis);
		}
	}

}
