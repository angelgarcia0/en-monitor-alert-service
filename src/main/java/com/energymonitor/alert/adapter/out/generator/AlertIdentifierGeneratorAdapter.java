package com.energymonitor.alert.adapter.out.generator;

import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates {@code VARCHAR(10)} business identifiers using {@link SecureRandom}.
 *
 * <p>Format: 10 characters from the alphabet {@code ABCDEFGHJKMNPQRSTUVWXYZ23456789}
 * (no ambiguous characters: no 0/O, 1/I/L).
 *
 * <p>Collision risk: 32^10 ≈ 1.1 × 10^15 possible values. With 1000 existing rows,
 * the probability of collision is approximately 1 in 1.1 × 10^12 per generation.
 * The primary key is the final guarantee: a collision would cause a constraint violation.
 *
 * <p>The class name carries the module prefix because Spring derives the bean name from
 * the simple class name and other modules already register their own generator adapters;
 * an unprefixed duplicate would clash at context startup.
 */
@Component
public class AlertIdentifierGeneratorAdapter implements IdentifierGeneratorPort {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 10;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
