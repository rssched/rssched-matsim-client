package ch.sbb.rssched.client.pipeline.scenario;

import ch.sbb.rssched.client.pipeline.core.DataSource;
import ch.sbb.rssched.client.pipeline.utils.io.ScenarioLoader;

/**
 * Loads a scenario for further processing in a pipeline,
 *
 * @author munterfi
 */
class ScenarioSource implements DataSource<ScenarioPipe> {
    private final String runId;
    private final String inputFolder;

    /**
     * Constructs a ScenarioLoader object with the specified run ID and input folder.
     *
     * @param runId       the ID of the simulation run
     * @param inputFolder the folder containing the output files of the run
     */
    public ScenarioSource(String runId, String inputFolder) {
        this.runId = runId;
        this.inputFolder = inputFolder;
    }

    @Override
    public ScenarioPipe fetch() {
        return new ScenarioPipe(runId, new ScenarioLoader(runId, inputFolder).load(true));
    }
}
