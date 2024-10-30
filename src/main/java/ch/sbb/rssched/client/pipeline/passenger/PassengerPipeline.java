package ch.sbb.rssched.client.pipeline.passenger;

import ch.sbb.rssched.client.config.selection.FilterStrategy;
import ch.sbb.rssched.client.pipeline.core.Pipeline;

/**
 * Extract passenger related information from a scenario.
 *
 * @author munterfi
 */
public class PassengerPipeline extends Pipeline<PassengerPipe> {

    /**
     * Constructs a RepPipeline with the specified parameters.
     *
     * @param instanceId      the ID of the RSSched instance (=request) to solve
     * @param runId           the ID of the scenario run
     * @param inputDirectory  the input directory containing the scenario data (output files of the simulation)
     * @param outputDirectory the output directory to export the processed scenario files
     * @param filterStrategy  the filter strategy for filtering transit lines
     * @param capacityFactor  the factor to adjust the passenger capacity of units to reflect deviations in passenger
     *                        demand
     */
    public PassengerPipeline(String instanceId, String runId, String inputDirectory, String outputDirectory, FilterStrategy filterStrategy, double sampleSize, double capacityFactor, int seatDurationThreshold) {
        // set source
        super(new EventSource(runId, inputDirectory));
        // add filters
        addFilter(new TransitLineFilter(filterStrategy));
        addFilter(new EventAnalysisFilter(sampleSize, seatDurationThreshold));
        // add sink
        addSink(new PassengerCSVWriter(outputDirectory, instanceId, capacityFactor));
    }
}
