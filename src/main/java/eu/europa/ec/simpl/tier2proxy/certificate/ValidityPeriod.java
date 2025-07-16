package eu.europa.ec.simpl.tier2proxy.certificate;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class ValidityPeriod {
    record CertificateValidityPeriod(Date before, Date after) {}

    private final int beforeDateAmountToSubtract;
    private final int afterDateAmountToAdd;

    @NonNull private final ChronoUnit beforeDateAmountToSubtractUnit;

    @NonNull private final ChronoUnit afterDateAmountToAddUnit;

    CertificateValidityPeriod from() {
        var beforeInstant = Instant.now()
                .atZone(ZoneId.systemDefault())
                .minus(beforeDateAmountToSubtract, beforeDateAmountToSubtractUnit)
                .toInstant();
        var afterInstant = Year.now()
                .plus(afterDateAmountToAdd, afterDateAmountToAddUnit)
                .atDay(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        return new CertificateValidityPeriod(Date.from(beforeInstant), Date.from(afterInstant));
    }
}
