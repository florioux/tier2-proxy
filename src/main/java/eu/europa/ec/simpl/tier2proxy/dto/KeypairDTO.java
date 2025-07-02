package eu.europa.ec.simpl.tier2proxy.dto;

import lombok.Data;

@Data
public class KeypairDTO {
    private byte[] publicKey;
    private byte[] privateKey;
}
