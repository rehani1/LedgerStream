package com.ledgerstream.config;

import com.ledgerstream.config.properties.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfiguration {

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(properties.allowedMethods());
		configuration.setAllowedHeaders(properties.allowedHeaders());
		configuration.setExposedHeaders(properties.exposedHeaders());
		configuration.setAllowCredentials(properties.allowCredentials());
		configuration.setMaxAge(properties.maxAge().toSeconds());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
