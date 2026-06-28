package com.ledgerstream.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingFilterTest {

	@Test
	void logsCompletedRequestsWithSafeContextFields() throws Exception {
		RequestLoggingFilter filter = new RequestLoggingFilter();
		Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
		request.setQueryString("token=secret");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = (servletRequest, servletResponse) -> {
			servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders");
			((HttpServletResponse) servletResponse).setStatus(HttpStatus.CREATED.value());
		};

		MDC.put("requestId", "request-123");
		try {
			filter.doFilter(request, response, chain);

			assertThat(appender.list).hasSize(1);
			ILoggingEvent event = appender.list.getFirst();
			assertThat(event.getFormattedMessage()).isEqualTo("http_request completed");
			Map<String, String> mdc = event.getMDCPropertyMap();
			assertThat(mdc)
				.containsEntry("requestId", "request-123")
				.containsEntry("httpMethod", "POST")
				.containsEntry("httpPath", "/api/orders")
				.containsEntry("endpoint", "/api/orders")
				.containsEntry("httpStatus", "201");
			assertThat(mdc.get("latencyMs")).matches("\\d+");
			assertThat(mdc.values()).noneMatch(value -> value.contains("token=secret"));
			assertThat(MDC.get("httpMethod")).isNull();
			assertThat(MDC.get("latencyMs")).isNull();
		} finally {
			MDC.clear();
			logger.detachAppender(appender);
			appender.stop();
		}
	}
}
