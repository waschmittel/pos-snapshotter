package de.flubba;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

public class CameraTest {

    @Test
    void testDetectCameraNames() {
        String[] names = Camera.detectCameraNames();
        System.out.println("Detected camera names: " + Arrays.toString(names));

        assertThat(names).isNotNull();
        assertThat(names.length).isGreaterThanOrEqualTo(1);
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            assertThat(names[0]).isNotEqualTo("No camera found");
        }
    }
}
