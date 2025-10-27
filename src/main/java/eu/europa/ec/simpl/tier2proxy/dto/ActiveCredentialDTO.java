package eu.europa.ec.simpl.tier2proxy.dto;

import eu.europa.ec.simpl.tier2proxy.enums.CredentialStatusDTO;
import java.time.Instant;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ActiveCredentialDTO {
    private String content;
    private CredentialStatusDTO status;
    private Instant issuanceDate;
    private Instant expiryDate;
    private String credentialId;
}
