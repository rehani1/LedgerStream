package com.ledgerstream.seed;

import java.util.Locale;

import com.ledgerstream.config.properties.DemoSeedProperties;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "ledgerstream.demo-seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final DemoSeedProperties properties;
	private final UserRepository userRepository;
	private final PortfolioRepository portfolioRepository;
	private final PasswordEncoder passwordEncoder;

	public DemoDataSeeder(
		DemoSeedProperties properties,
		UserRepository userRepository,
		PortfolioRepository portfolioRepository,
		PasswordEncoder passwordEncoder
	) {
		this.properties = properties;
		this.userRepository = userRepository;
		this.portfolioRepository = portfolioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedUser(properties.email(), properties.password(), UserRole.USER);
		if (properties.adminEnabled()) {
			seedUser(properties.adminEmail(), properties.adminPassword(), UserRole.ADMIN);
		}
		log.info("Demo seed data verified for local profile");
	}

	private User seedUser(String email, String password, UserRole role) {
		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByEmail(normalizedEmail)
			.map(existingUser -> ensureRole(existingUser, role))
			.orElseGet(() -> createDemoUser(normalizedEmail, password, role));
		portfolioRepository.findByUserId(user.getId()).orElseGet(() -> createDemoPortfolio(user));
		return user;
	}

	private User createDemoUser(String email, String password, UserRole role) {
		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(role);
		return userRepository.save(user);
	}

	private User ensureRole(User user, UserRole role) {
		if (user.getRole() == role) {
			return user;
		}
		user.setRole(role);
		return userRepository.save(user);
	}

	private Portfolio createDemoPortfolio(User user) {
		Portfolio portfolio = new Portfolio();
		portfolio.setUser(user);
		portfolio.setCashBalance(properties.initialCash());
		portfolio.setBaseCurrency("USD");
		return portfolioRepository.save(portfolio);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
