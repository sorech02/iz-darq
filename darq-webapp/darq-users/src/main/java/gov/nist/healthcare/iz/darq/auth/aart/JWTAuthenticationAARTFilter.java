package gov.nist.healthcare.iz.darq.auth.aart;

import com.google.common.base.Strings;
import gov.nist.healthcare.auth.service.AuthenticationService;
import gov.nist.healthcare.domain.OpAck;
import gov.nist.healthcare.iz.darq.access.domain.UserRole;
import gov.nist.healthcare.iz.darq.access.service.ConfigurableService;
import gov.nist.healthcare.iz.darq.model.ToolConfigurationKey;
import gov.nist.healthcare.iz.darq.model.ToolConfigurationKeyValue;
import gov.nist.healthcare.iz.darq.model.ToolConfigurationProperty;
import gov.nist.healthcare.iz.darq.users.domain.RemoteAccountCreationRequest;
import gov.nist.healthcare.iz.darq.users.domain.User;
import gov.nist.healthcare.iz.darq.users.domain.UserAccount;
import gov.nist.healthcare.iz.darq.users.exception.RequestValidationException;
import gov.nist.healthcare.iz.darq.users.service.impl.UserManagementService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class JWTAuthenticationAARTFilter extends AbstractAuthenticationProcessingFilter implements ConfigurableService {

    public static final String AART_CONNECT_EXCEPTION = "AART_CONNECT_EXCEPTION";
    private final UserManagementService userManagementService;
    private final AuthenticationService<UserAccount, UserRole, User> authenticationService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final Environment environment;
    private String publicKey;

    public JWTAuthenticationAARTFilter(String URL, UserManagementService userManagementService, AuthenticationManager manager, AuthenticationService<UserAccount, UserRole, User> authenticationService, AuthenticationEntryPoint authenticationEntryPoint, Environment environment) {
        super(new AntPathRequestMatcher(URL));
        this.userManagementService = userManagementService;
        this.authenticationService = authenticationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.environment = environment;
        setAuthenticationManager(manager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        try {
            String path = request.getParameter("path");
            String token = request.getParameter("token");
            if (!Strings.isNullOrEmpty(token)) {
                Jws<Claims> jwt = Jwts.parser()
                        .setSigningKey(this.publicKey.getBytes(StandardCharsets.UTF_8))
                        .parseClaimsJws(token);
                UserAccount user = this.getOrCreateUser(jwt);
                return new JWTAuthenticationAARTToken(user, jwt, path, user.getAuthorities());
            } else {
                this.authenticationEntryPoint.commence(request, response, new AuthenticationServiceException("No token provided"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.authenticationService.clearLoginCookie(response);
            request.setAttribute(AART_CONNECT_EXCEPTION, e);
            request.getRequestDispatcher("/api/aart/error").forward(request, response);
        }

        return null;
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest req, HttpServletResponse res, FilterChain chain, Authentication auth) {
        try {
            if(auth instanceof JWTAuthenticationAARTToken) {
                JWTAuthenticationAARTToken token = (JWTAuthenticationAARTToken) auth;
                Cookie authCookie = this.authenticationService.createAuthCookie(
                        token.getPrincipal(),
                        token.getCredentials().getBody().getExpiration(),
                        token.getCredentials().getBody().get("facility", ArrayList.class)
                );
                res.setContentType("application/json");
                res.addCookie(authCookie);
                res.sendRedirect(token.getTargetPath());
            } else {
                authenticationEntryPoint.commence(req, res, new AuthenticationServiceException(""));
            }
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException | ServletException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        authenticationEntryPoint.commence(request, response, failed);
    }

    private UserAccount getOrCreateUser(Jws<Claims> jwt) throws RequestValidationException {
        String issuer = jwt.getBody().getIssuer();
        String subject = jwt.getBody().getSubject();
        UserAccount user = this.userManagementService.findUserAccountByIssuer(issuer, subject);
        if(user != null) {
            return user;
        } else {
            return this.userManagementService.registerRemoteAccount(new RemoteAccountCreationRequest(
                    issuer,
                    subject,
                    jwt.getBody().get("email", String.class),
                    jwt.getBody().get("name", String.class),
                    jwt.getBody().get("org", String.class)
            ));
        }
    }

    @Override
    public boolean shouldReloadOnUpdate(Set<ToolConfigurationKeyValue> keyValueSet) {
        return keyValueSet.stream().anyMatch(k -> k.getKey().equals("aart.connector.publicKey.location"));
    }

    @Override
    public void configure(Properties properties) {
        this.publicKey = properties.getProperty("aart.connector.publicKey.location");
    }

    @Override
    public Set<ToolConfigurationProperty> initialize() {
        return new HashSet<>(
                Collections.singletonList(new ToolConfigurationProperty("aart.connector.publicKey.location", "/Users/hnt5/darq-keys/aart.pub", true))
        );
    }

    @Override
    public Set<ToolConfigurationKey> getConfigurationKeys() {
        return new HashSet<>(
                Collections.singletonList(new ToolConfigurationKey("aart.connector.publicKey.location", true))
        );
    }

    @Override
    public OpAck<Void> checkServiceStatus() {
        if(publicKey == null || publicKey.isEmpty() || !publicKey.equals("/Users/hnt5/darq-keys/aart.pub")) {
            return new OpAck<>(OpAck.AckStatus.FAILED, "AART public key not found", null, "AART CONNECTOR");
        } else {
            return new OpAck<>(OpAck.AckStatus.SUCCESS, "AART public key valid", null, "AART CONNECTOR");
        }
    }

    @Override
    public String getServiceDisplayName() {
        return "AART CONNECTOR";
    }
}
