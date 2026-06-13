package com.ledgerstream.auth;

import java.time.Instant;

import com.ledgerstream.config.properties.JwtProperties;
import com.ledgerstream.domain.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final JwtProperties properties;
	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;

	public JwtService(JwtProperties properties, JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
		this.properties = properties;
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
	}

	public AccessToken issueAccessToken(User user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.subject(user.getId().toString())
			.claim("email", user.getEmail())
			.claim("role", user.getRole().name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, expiresAt);
	}

	public Jwt decode(String token) {
		return jwtDecoder.decode(token);
	}

	public record AccessToken(String value, Instant expiresAt) {
	}
}
