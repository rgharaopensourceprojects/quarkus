package io.quarkus.test.junit.nativeimage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Assertions;

/**
 * This is a general utility to assert via
 * unit testing how many classes, methods, objects etc. have been included in a native-image.
 * <p>
 * For detailed information and explanations on the build output, visit
 * <a href="https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md">the upstream GraalVM
 * documentation</a>.
 */
public class BuildOutput {

    private static final String IMAGE_METRICS_TEST_PROPERTIES = "image-metrics-test.properties";
    private final JsonObject buildOutput;

    public BuildOutput() {
        this.buildOutput = getBuildOutput();
    }

    public void verifyImageMetrics() {
        verifyImageMetrics(IMAGE_METRICS_TEST_PROPERTIES);
    }

    public void verifyImageMetrics(String propertiesFileName) {
        Properties properties = getProperties(propertiesFileName);

        properties.forEach((key, value) -> {
            if (((String) key).endsWith(".tolerance")) {
                return;
            }
            String[] keyParts = ((String) key).split("\\.");
            String tolerance = properties.getProperty(key + ".tolerance");
            assert tolerance != null : "tolerance not defined for " + key;
            assertValueWithinRange(Integer.parseInt((String) value), Integer.parseInt(tolerance), keyParts);
        });
    }

    private Properties getProperties(String propertiesFileName) {
        Properties properties = new Properties();
        try {
            properties.load(getClass().getClassLoader().getResourceAsStream(propertiesFileName));
        } catch (IOException e) {
            Assertions.fail("Could not load properties from " + propertiesFileName, e);
        }
        return properties;
    }

    private void assertValueWithinRange(int expectedValue, int tolerancePercentage, String... key) {
        JsonObject currentObject = buildOutput;
        for (int i = 0; i < key.length - 1; i++) {
            currentObject = currentObject.getJsonObject(key[i]);
        }
        String lastKey = key[key.length - 1];
        int actualValue = currentObject.getInt(lastKey);
        Assertions.assertTrue(isNumberWithinRange(expectedValue, actualValue, tolerancePercentage),
                "Expected " + String.join(".", key) + " to be within range [" + expectedValue + " +- " + tolerancePercentage
                        + "%] but was " + actualValue);
    }

    private boolean isNumberWithinRange(int expectedNumberOfClasses, int actualNumberOfClasses, int tolerancePercentage) {
        final int lowerBound = expectedNumberOfClasses - (expectedNumberOfClasses * tolerancePercentage / 100);
        final int upperBound = expectedNumberOfClasses + (expectedNumberOfClasses * tolerancePercentage / 100);
        return actualNumberOfClasses >= lowerBound && actualNumberOfClasses <= upperBound;
    }

    private static JsonObject getBuildOutput() {
        final Path buildOutputPath = getBuildOutputPath();
        try (InputStream inputStream = Files.newInputStream(buildOutputPath)) {
            return Json.createReader(inputStream).readObject();
        } catch (Exception e) {
            throw new RuntimeException("Could not load build output", e);
        }
    }

    private static Path getBuildOutputPath() {
        final Path buildDirectory = locateNativeImageBuildDirectory();
        final File[] buildOutput = buildDirectory.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT)
                .endsWith("-build-output-stats.json"));
        Assertions.assertNotNull(buildOutput, "Could not identify the native image build output");
        Assertions.assertEquals(1, buildOutput.length, "Could not identify the native image build output");
        return buildOutput[0].toPath();
    }

    private static Path locateNativeImageBuildDirectory() {
        Path buildPath = Paths.get("target");
        final File[] files = buildPath.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT)
                .endsWith("-native-image-source-jar"));
        Assertions.assertNotNull(files, "Could not identify the native image build directory");
        Assertions.assertEquals(1, files.length, "Could not identify the native image build directory");
        return files[0].toPath();
    }
}
