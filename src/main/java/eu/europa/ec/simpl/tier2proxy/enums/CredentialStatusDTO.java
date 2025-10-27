package eu.europa.ec.simpl.tier2proxy.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CredentialStatusDTO {
    VALID("VALID"),
    EXPIRED("EXPIRED"),
    REVOKED("REVOKED"),
    SUSPENDED("SUSPENDED");

    @Getter
    private final String status;
}
