package ch.sbb.rssched.client.pipeline.passenger;

import ch.sbb.rssched.client.pipeline.core.DataSource;
import ch.sbb.rssched.client.pipeline.utils.io.ScenarioLoader;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Loads a scenario with events file for further processing in a pipeline.
 *
 * @author munterfi
 */
class EventSource implements DataSource<PassengerPipe> {
    private final String runId;
    private final String inputFolder;

    /**
     * Constructs a ScenarioLoader object with the specified run ID and input folder.
     *
     * @param runId       the ID of the simulation run
     * @param inputFolder the folder containing the output files of the run
     */
    public EventSource(String runId, String inputFolder) {
        this.runId = runId;
        this.inputFolder = inputFolder;
    }

    @Override
    public PassengerPipe fetch() {
        var scenarioLoader = new ScenarioLoader(runId, inputFolder);
        return new PassengerPipe(runId, scenarioLoader.getEventsFile(), scenarioLoader.load(false), new HashSet<>(),
                new ArrayList<>());
    }
}
