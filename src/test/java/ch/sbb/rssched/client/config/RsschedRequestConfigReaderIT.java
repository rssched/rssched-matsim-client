package ch.sbb.rssched.client.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RsschedRequestConfigReaderIT {

    private RsschedRequestConfigReader reader;

    @BeforeEach
    void setUp() {
        reader = new RsschedRequestConfigReader();
    }

    @Test
    void shouldReadConfigFile() throws IOException {
        URL filePath = getClass().getResource("/ch/sbb/rssched/client/config/request_config.xlsx");
        assert filePath != null;
        RsschedRequestConfig config = reader.readExcelFile(filePath.getPath());

        assertNotNull(config);
        assertEquals("rss001", config.getInstanceId());
        assertEquals("run_1", config.getRunId());
        assertEquals("input/folder/path", config.getInputDirectory());
        assertEquals("output/folder/path", config.getOutputDirectory());

        // global settings
        assertEquals(0.1, config.getGlobal().getSampleSize());
        assertEquals(Set.of("pt"), config.getGlobal().getAllowedModes());
        assertEquals(90 / 3.6, config.getGlobal().getDeadHeadTripSpeedLimit());
        assertFalse(config.getGlobal().isForbidDeadHeadTrips());
        assertEquals(1.33, config.getGlobal().getCapacityFactor());
        assertEquals(4, config.getGlobal().getVehicleTypes().size());

        // depot settings
        assertEquals(999, config.getDepot().getDefaultCapacity());
        assertEquals("dpt_", config.getDepot().getDefaultIdPrefix());
        assertFalse(config.getDepot().isCreateAtTerminalLocations());
    }

}
