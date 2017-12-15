package gov.nist.healthcare.auth.config;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.nist.healthcare.auth.domain.LoginRequest;

public class JWTLoginFilter extends AbstractAuthenticationProcessingFilter {

	@Autowired
	private AuthenticationEntryPoint handler;
	private TokenAuthenticationService tokenService;
		
	public JWTLoginFilter(String url, AuthenticationManager authManager, TokenAuthenticationService tokenService) {
		super(new AntPathRequestMatcher(url));
		this.tokenService = tokenService;
		setAuthenticationManager(authManager);
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest req, HttpServletResponse res) throws AuthenticationException, IOException, ServletException {
		LoginRequest creds = new ObjectMapper().readValue(req.getInputStream(), LoginRequest.class);
		return getAuthenticationManager().authenticate(new UsernamePasswordAuthenticationToken(creds.getUsername(),creds.getPassword(), Collections.emptyList()));
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest req, HttpServletResponse res, FilterChain chain, Authentication auth) throws IOException, ServletException {
		tokenService.addAuthentication(res, auth.getName());
	}
	
	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
		handler.commence(request, response, failed);
	}
	
}