package com.ledgerstream.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.ledgerstream.logging.MdcScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		long startNanos = System.nanoTime();
		try {
			filterChain.doFilter(request, response);
		} finally {
			try (MdcScope ignored = requestLogContext(request, response, startNanos)) {
				log.info("http_request completed");
			}
		}
	}

	private MdcScope requestLogContext(HttpServletRequest request, HttpServletResponse response, long startNanos) {
		Map<String, Object> context = new LinkedHashMap<>();
		context.put("httpMethod", request.getMethod());
		context.put("httpPath", pathWithoutContext(request));
		context.put("endpoint", endpoint(request));
		context.put("httpStatus", response.getStatus());
		context.put("latencyMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
		return MdcScope.put(context);
	}

	private String endpoint(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern == null ? pathWithoutContext(request) : pattern.toString();
	}

	private String pathWithoutContext(HttpServletRequest request) {
		String contextPath = request.getContextPath();
		String uri = request.getRequestURI();
		if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

}
