package ch.sbb.rssched;

import ch.sbb.rssched.client.IntegrationTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationIT {

    private static final String CONFIG_FILE = "integration-test/input/de/kelheim/kelheim-v3.0/25pct/kelheim-v3.0-25pct.rssched_request_config_vehicle_type_filter.xlsx";
    private static final String SCHEDULER_BASE_URL = "http://localhost";
    private static final String SCHEDULER_PORT = "3000";

    @BeforeEach
    void setUp() throws IOException {
        new IntegrationTestData(false).setup();
    }

    @Test
    void testMainWithDryRun() throws Exception {
        String[] args = {CONFIG_FILE, "-d"};
        Application.runApplication(args);
    }

    @Test
    void testMainWithSendingToSolver() throws Exception {
        String[] args = {CONFIG_FILE, "-h", SCHEDULER_BASE_URL, "-p", SCHEDULER_PORT};
        Application.runApplication(args);
    }

    @Test
    void testMissingRequiredArgument() {
        String[] args = {};
        Exception exception = assertThrows(RuntimeException.class, () -> Application.runApplication(args));
        assertEquals("Missing required argument: config_file", exception.getMessage());
    }
}
