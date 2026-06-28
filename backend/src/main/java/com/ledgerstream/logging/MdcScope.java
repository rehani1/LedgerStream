package com.ledgerstream.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

public final class MdcScope implements AutoCloseable {

	private final Map<String, String> previousValues = new LinkedHashMap<>();
	private boolean closed;

	private MdcScope(Map<String, ?> values) {
		if (values == null) {
			return;
		}
		values.forEach(this::put);
	}

	public static MdcScope put(Map<String, ?> values) {
		return new MdcScope(values);
	}

	private void put(String key, Object value) {
		if (key == null || key.isBlank() || value == null) {
			return;
		}
		String text = value.toString();
		if (text.isBlank()) {
			return;
		}
		previousValues.put(key, MDC.get(key));
		MDC.put(key, text);
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		previousValues.forEach((key, previousValue) -> {
			if (previousValue == null) {
				MDC.remove(key);
				return;
			}
			MDC.put(key, previousValue);
		});
	}
}
