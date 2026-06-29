package com.ledgerstream.archive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;

public class HttpPutArchiveSink implements ArchiveSink {

	private final URI baseUri;
	private final String authorizationHeader;
	private final HttpClient httpClient;
	private final Clock clock;

	public HttpPutArchiveSink(String baseUrl, String authorizationHeader, HttpClient httpClient, Clock clock) {
		this.baseUri = URI.create(ensureTrailingSlash(baseUrl));
		this.authorizationHeader = authorizationHeader;
		this.httpClient = httpClient;
		this.clock = clock;
	}

	@Override
	public ArchiveLocation write(String key, byte[] content, String contentType) {
		URI uri = baseUri.resolve(key);
		HttpRequest.Builder request = HttpRequest.newBuilder(uri)
			.PUT(HttpRequest.BodyPublishers.ofByteArray(content))
			.header("Content-Type", contentType);
		if (authorizationHeader != null && !authorizationHeader.isBlank()) {
			request.header("Authorization", authorizationHeader.trim());
		}

		try {
			HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ArchiveException("Archive HTTP PUT failed with status " + response.statusCode());
			}
			return new ArchiveLocation(
				key,
				uri.toString(),
				content.length,
				ArchiveChecksums.sha256Hex(content),
				Instant.now(clock)
			);
		} catch (IOException ex) {
			throw new ArchiveException("Archive HTTP PUT failed", ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ArchiveException("Archive HTTP PUT was interrupted", ex);
		}
	}

	private static String ensureTrailingSlash(String value) {
		return value.endsWith("/") ? value : value + "/";
	}
}
