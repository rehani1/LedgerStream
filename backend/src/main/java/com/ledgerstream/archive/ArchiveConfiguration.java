package com.ledgerstream.archive;

import java.net.http.HttpClient;
import java.time.Clock;

import com.ledgerstream.config.properties.ArchiveProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchiveConfiguration {

	@Bean
	ArchiveSink archiveSink(ArchiveProperties properties, Clock clock) {
		if (!properties.enabled()) {
			return new DisabledArchiveSink();
		}
		return switch (properties.backend()) {
			case FILESYSTEM -> new LocalFilesystemArchiveSink(properties.local().rootPath(), clock);
			case HTTP_PUT -> new HttpPutArchiveSink(requiredBaseUrl(properties), properties.httpPut().authorizationHeader(), HttpClient.newHttpClient(), clock);
		};
	}

	private String requiredBaseUrl(ArchiveProperties properties) {
		String baseUrl = properties.httpPut().baseUrl();
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new ArchiveException("HTTP PUT archive backend requires ledgerstream.archive.http-put.base-url");
		}
		return baseUrl.trim();
	}
}
