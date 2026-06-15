package io.camunda.tests;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;

final class BedrockIntegrationSupport {

    private BedrockIntegrationSupport() {}

    static void prepareSystemPropertiesFromEnvironment() {
        normalizeProperty("aws.bedrock.region", "AWS_BEDROCK_REGION", false);
        normalizeProperty("aws.bedrock.access.key", "AWS_BEDROCK_ACCESS_KEY", false);
        normalizeProperty("aws.bedrock.secret.key", "AWS_BEDROCK_SECRET_KEY", false);
        normalizeProperty("aws.bedrock.session.token", "AWS_BEDROCK_SESSION_TOKEN", false);
        normalizeProperty("aws.bedrock.model", "AWS_BEDROCK_MODEL", true);
    }

    static void assumeReady() {
        var runBedrock = read("run.bedrock.tests", "RUN_BEDROCK_TESTS");
        Assumptions.assumeTrue(
            isTrue(runBedrock),
            "Skipping Bedrock-dependent integration test. Set RUN_BEDROCK_TESTS=true to enable.");

        var region = read("aws.bedrock.region", "AWS_BEDROCK_REGION");
        var accessKey = read("aws.bedrock.access.key", "AWS_BEDROCK_ACCESS_KEY");
        var secretKey = read("aws.bedrock.secret.key", "AWS_BEDROCK_SECRET_KEY");
        var model = read("aws.bedrock.model", "AWS_BEDROCK_MODEL");

        var missing = new StringBuilder();
        if (isBlank(region)) {
            missing.append(" AWS_BEDROCK_REGION");
        }
        if (isBlank(accessKey)) {
            missing.append(" AWS_BEDROCK_ACCESS_KEY");
        }
        if (isBlank(secretKey)) {
            missing.append(" AWS_BEDROCK_SECRET_KEY");
        }
        if (isBlank(model)) {
            missing.append(" AWS_BEDROCK_MODEL");
        }

        Assumptions.assumeTrue(
            missing.length() == 0,
            "Skipping Bedrock-dependent integration test. Missing required settings:" + missing);

        Assumptions.assumeTrue(
            !containsEncodedArn(model),
            "Skipping Bedrock-dependent integration test. AWS_BEDROCK_MODEL still appears URL-encoded after normalization: "
                + model);
    }

    private static void normalizeProperty(String propertyName, String envName, boolean decodeUrlEncoding) {
        var value = trimToNull(System.getProperty(propertyName));
        if (value == null) {
            value = trimToNull(System.getenv(envName));
        }
        if (value == null) {
            return;
        }
        if (decodeUrlEncoding) {
            value = maybeDecode(value);
        }
        System.setProperty(propertyName, value);
    }

    private static String read(String propertyName, String envName) {
        var value = trimToNull(System.getProperty(propertyName));
        if (value != null) {
            return value;
        }
        return trimToNull(System.getenv(envName));
    }

    private static String maybeDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static boolean containsEncodedArn(String value) {
        if (value == null) {
            return false;
        }
        var lower = value.toLowerCase();
        return lower.contains("arn%3a") || lower.contains("%253a") || lower.contains("%2f");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isTrue(String value) {
        if (value == null) {
            return false;
        }
        var normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
}