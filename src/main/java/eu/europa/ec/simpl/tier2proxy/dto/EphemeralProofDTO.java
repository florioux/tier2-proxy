package eu.europa.ec.simpl.tier2proxy.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EphemeralProofDTO {
    private String proof;
}
