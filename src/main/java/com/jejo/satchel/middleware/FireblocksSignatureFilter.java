package com.jejo.satchel.middleware;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FireblocksSignatureFilter extends OncePerRequestFilter {

    @Value("${custodian.api.public-key}")
    private String fireblocksPublicKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getServletPath();

        // Only apply filter to webhook endpoint
        if (!path.contains("/api/v1/accounts/funds_transfer")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request to allow multiple reads (only for webhook paths)
        MultiReadHttpServletRequest wrappedRequest = new MultiReadHttpServletRequest(request);
        String requestBody = wrappedRequest.getBody();

        // Get Fireblocks-Signature header
        String signature = request.getHeader("Fireblocks-Signature");
        if (signature == null) {
            log.error("Missing Fireblocks-Signature header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing signature");
            return;
        }
        
        // Verify signature
        try {
            if (!verifySignature(requestBody, signature, fireblocksPublicKey)) {
                log.error("Invalid Fireblocks-Signature");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid signature");
                return;
            }
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Signature verification failed");
            return;
        }

        // Proceed to controller with wrapped request
        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean verifySignature(String message, String signature, String publicKeyPem) throws Exception {
        // Clean and parse public key
        String cleanedPublicKey = publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\n", "").trim();
        byte[] publicKeyBytes = Base64.getDecoder().decode(cleanedPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        // Initialize RSA-SHA512 verifier
        Signature verifier = Signature.getInstance("SHA512withRSA");
        verifier.initVerify(publicKey);
        verifier.update(message.getBytes("UTF-8"));

        // Decode and verify signature
        byte[] signatureBytes = Base64.getDecoder().decode(signature);
        return verifier.verify(signatureBytes);
    }
}
