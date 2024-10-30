package ch.sbb.rssched.client.pipeline.utils.io;

import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.vehicles.MatsimVehicleReader;

@Log4j2
public class ScenarioLoader {
    private static final String NETWORK_FILE = "output_network.xml.gz";
    private static final String TRANSIT_SCHEDULE_FILE = "output_transitSchedule.xml.gz";
    private static final String TRANSIT_VEHICLES_FILE = "output_transitVehicles.xml.gz";
    private static final String EVENTS_FILE = "output_events.xml.gz";
    private final String runId;
    private final String inputFolder;

    /**
     * Constructs a ScenarioLoader object with the specified run ID, input folder.
     *
     * @param runId       the ID of the simulation run
     * @param inputFolder the folder containing the output files of the run
     */
    public ScenarioLoader(String runId, String inputFolder) {
        this.runId = runId;
        this.inputFolder = inputFolder;
    }

    public Scenario load(boolean network) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        if (network) {
            new MatsimNetworkReader(scenario.getNetwork()).readFile(buildPath(NETWORK_FILE));
        }

        new TransitScheduleReader(scenario).readFile(buildPath(TRANSIT_SCHEDULE_FILE));
        new MatsimVehicleReader(scenario.getTransitVehicles()).readFile(buildPath(TRANSIT_VEHICLES_FILE));

        return scenario;
    }

    public String getEventsFile() {
        return buildPath(EVENTS_FILE);
    }

    private String buildPath(String fileType) {
        if (inputFolder.endsWith("/")) {
            return String.format("%s%s.%s", inputFolder, runId, fileType);
        } else {
            return String.format("%s/%s.%s", inputFolder, runId, fileType);
        }
    }

}
