package com.ledgerstream.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.ledgerstream.config.properties.DemoSeedProperties;
import com.ledgerstream.domain.model.Portfolio;
import com.ledgerstream.domain.model.User;
import com.ledgerstream.domain.model.UserRole;
import com.ledgerstream.domain.repository.PortfolioRepository;
import com.ledgerstream.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PortfolioRepository portfolioRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void createsDemoUserAndPortfolioWhenEnabled() {
		DemoSeedProperties properties = new DemoSeedProperties(
			true,
			"Demo@Example.com",
			"Password123!",
			new BigDecimal("100000.00"),
			false,
			"admin@example.com",
			"AdminPassword123!"
		);
		when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});
		when(portfolioRepository.findByUserId(any(UUID.class))).thenReturn(Optional.empty());
		when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DemoDataSeeder seeder = new DemoDataSeeder(properties, userRepository, portfolioRepository, passwordEncoder);
		seeder.run(null);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User user = userCaptor.getValue();
		assertThat(user.getEmail()).isEqualTo("demo@example.com");
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(passwordEncoder.matches("Password123!", user.getPasswordHash())).isTrue();

		ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
		verify(portfolioRepository).save(portfolioCaptor.capture());
		Portfolio portfolio = portfolioCaptor.getValue();
		assertThat(portfolio.getUser()).isSameAs(user);
		assertThat(portfolio.getCashBalance()).isEqualByComparingTo("100000.00");
		assertThat(portfolio.getBaseCurrency()).isEqualTo("USD");
	}

	@Test
	void createsDemoAdminWhenEnabled() {
		DemoSeedProperties properties = new DemoSeedProperties(
			true,
			"demo@example.com",
			"Password123!",
			new BigDecimal("100000.00"),
			true,
			"Admin@Example.com",
			"AdminPassword123!"
		);
		when(userRepository.findByEmail("demo@example.com")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});
		when(portfolioRepository.findByUserId(any(UUID.class))).thenReturn(Optional.empty());
		when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DemoDataSeeder seeder = new DemoDataSeeder(properties, userRepository, portfolioRepository, passwordEncoder);
		seeder.run(null);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository, times(2)).save(userCaptor.capture());

		assertThat(userCaptor.getAllValues())
			.extracting(User::getEmail, User::getRole)
			.contains(
				tuple("demo@example.com", UserRole.USER),
				tuple("admin@example.com", UserRole.ADMIN)
			);
		assertThat(userCaptor.getAllValues())
			.filteredOn(user -> user.getRole() == UserRole.ADMIN)
			.allMatch(user -> passwordEncoder.matches("AdminPassword123!", user.getPasswordHash()));
	}
}
