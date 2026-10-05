package com.paytm.seatreservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenFilter bearerTokenFilter)
			throws Exception {

		http.csrf(csrf -> csrf.disable())

				.formLogin(form -> form.disable())

				.httpBasic(basic -> basic.disable())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth -> auth

						.requestMatchers("/actuator/health/**", "/actuator/health","/actuator/prometheus").permitAll()
						
						.requestMatchers(HttpMethod.GET,   "/shows", "/shows/**").permitAll()

						.requestMatchers(HttpMethod.POST, "/shows").hasRole("ADMIN")

						.requestMatchers(HttpMethod.POST, "/shows/*/reserve").authenticated()

						.requestMatchers(HttpMethod.POST, "/reservations/*/cancel").authenticated()

						.anyRequest().denyAll())

				.addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
