package com.example.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class AuthTokenController {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenController.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final JwtEncoder encoder;

    public AuthTokenController(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    @PostMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> issueToken(Authentication authentication) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(authentication.getName())
                .claim("department", "reports")
                .issuer(ApiSecurityConfiguration.ISSUER)
                .audience(List.of(ApiSecurityConfiguration.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.info("Issued API access token subject={}", authentication.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", TOKEN_LIFETIME.toSeconds()));
    }
}
