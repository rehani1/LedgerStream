package com.ledgerstream.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!test")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String token = bearerToken(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				authenticate(token);
			} catch (JwtException | IllegalArgumentException ex) {
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String token) {
		Jwt jwt = jwtService.decode(token);
		UUID userId = UUID.fromString(jwt.getSubject());
		User user = userRepository.findById(userId).orElseThrow(() -> new JwtException("User no longer exists"));
		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			principal,
			token,
			List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private String bearerToken(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return null;
		}
		return authorization.substring("Bearer ".length()).trim();
	}
}
