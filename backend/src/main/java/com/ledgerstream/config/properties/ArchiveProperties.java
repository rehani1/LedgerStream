package com.ledgerstream.config.properties;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgerstream.archive")
public record ArchiveProperties(
	boolean enabled,
	Backend backend,
	Local local,
	HttpPut httpPut
) {

	public ArchiveProperties {
		if (backend == null) {
			backend = Backend.FILESYSTEM;
		}
		if (local == null) {
			local = new Local(null);
		}
		if (httpPut == null) {
			httpPut = new HttpPut(null, null);
		}
	}

	public enum Backend {
		FILESYSTEM,
		HTTP_PUT
	}

	public record Local(Path rootPath) {

		public Local {
			if (rootPath == null) {
				rootPath = Path.of("./data/archives");
			}
		}
	}

	public record HttpPut(String baseUrl, String authorizationHeader) {
	}
}
