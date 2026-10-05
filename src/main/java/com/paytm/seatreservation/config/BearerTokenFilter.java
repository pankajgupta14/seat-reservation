package com.paytm.seatreservation.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String authorization = request.getHeader("Authorization");

		System.out.println(">>> REQUEST: " + request.getMethod() + " " + request.getRequestURI());
		System.out.println(">>> AUTH HEADER: " + authorization);
		
		if (authorization != null && authorization.startsWith("Bearer ")) {

			String token = authorization.substring(7).trim();

			if (!token.isBlank()) {
				String userId = token;

				String role = "admin".equalsIgnoreCase(userId) ? "ROLE_ADMIN" : "ROLE_USER";

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId,
						null, List.of(new SimpleGrantedAuthority(role)));

				SecurityContextHolder.getContext().setAuthentication(authentication);
				System.out.println(">>> AUTHENTICATED USER: " + userId);
				System.out.println(">>> AUTHORITIES: " + authentication.getAuthorities());
			}
		}
		filterChain.doFilter(request, response);
	}

}
