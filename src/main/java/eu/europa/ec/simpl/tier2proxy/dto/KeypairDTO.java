package eu.europa.ec.simpl.tier2proxy.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KeypairDTO {
    private String csr;
    private UUID id;
    private String name;
    private Boolean active;
    private String publicKey;
    private Instant creationTimestamp;
    private String privateKey;
}
