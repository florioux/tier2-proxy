package eu.europa.ec.simpl.tier2proxy.util;

import io.netty.util.internal.StringUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class PropertyLoader {

    public static final char DOT = '.';
    public static final char UNDERSCORE = '_';
    public static final char DASH = '-';
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @SneakyThrows
    public static Properties loadPropertiesFromClasspath(String propertiesResource) {
        var props = new Properties();

        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesResource)) {
            props.load(in);
        }
        resolveProperties(props);
        log.info("Loaded {} properties from {}", props.size(), propertiesResource);
        return props;
    }

    public static Properties loadPropertiesFromFile(String filePath) {

        var props = new Properties();

        var path = Paths.get(filePath);
        if (Files.exists(path)) {
            try (var fis = Files.newInputStream(path)) {
                props.load(fis);
                resolveProperties(props);
            } catch (IOException e) {
                log.error("Failed to load properties from file: {}", filePath, e);
            }
        }
        return props;
    }

    private void resolveProperties(Properties props) {
        for (String key : props.stringPropertyNames()) {
            var originalValue = props.getProperty(key);
            var resolvedValue = replaceEnvPlaceholders(originalValue, props);

            resolvedValue = Optional.ofNullable(System.getenv(toUpperSnakeCase(key)))
                    .map(envOverride -> {
                        log.debug("Overriding property '{}' with environment variable value '{}'", key, envOverride);
                        return envOverride;
                    })
                    .orElse(resolvedValue);

            props.setProperty(key, resolvedValue);
        }
    }

    private static String replaceEnvPlaceholders(String value, Properties props) {

        var matcher = PLACEHOLDER_PATTERN.matcher(value);
        var sb = new StringBuilder();
        var found = false;

        while (matcher.find()) {
            found = true;
            var envVar = matcher.group(1);
            var replacement = Optional.ofNullable(System.getenv(envVar))
                    .or(() -> Optional.ofNullable(props.getProperty(envVar)))
                    .orElse(StringUtil.EMPTY_STRING);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        var resolved = sb.toString();

        return found ? replaceEnvPlaceholders(resolved, props) : resolved;
    }

    private static String toUpperSnakeCase(String key) {
        return key.toUpperCase(Locale.getDefault()).replace(DOT, UNDERSCORE).replace(DASH, UNDERSCORE);
    }
}
