package ch.sbb.rssched.client.pipeline.request;

import ch.sbb.rssched.client.config.RsschedRequestConfig;
import ch.sbb.rssched.client.pipeline.core.Pipeline;
import ch.sbb.rssched.client.pipeline.passenger.PassengerPipeline;
import ch.sbb.rssched.client.pipeline.scenario.ScenarioPipeline;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request export pipeline
 * <p>
 * Creates and exports a rolling stock scheduler request.
 *
 * @author munterfi
 */
public class RequestPipeline extends Pipeline<RequestPipe> {
    /**
     * Export Pipeline
     * <p>
     * Constructs a pipeline for creating rolling stock scheduler requests with the specified data source,
     * configuration, and output directory.
     *
     * @param config The request configuration containing various parameters for the scheduler request.
     */
    public RequestPipeline(RsschedRequestConfig config) {
        // set source
        super(new ScenarioPassengerCollector(config.getRunId(),
                new ScenarioPipeline(config.getInstanceId(), config.getRunId(), config.getInputDirectory(),
                        config.getOutputDirectory(), config.getGlobal().getFilterStrategy(),
                        collectTransitStopFacilitiesToKeep(config), config.getGlobal().getAllowedModes()),
                new PassengerPipeline(config.getInstanceId(), config.getRunId(), config.getInputDirectory(),
                        config.getOutputDirectory(), config.getGlobal().getFilterStrategy(),
                        config.getGlobal().getSampleSize(), config.getGlobal().getCapacityFactor(),
                        config.getGlobal().getSeatDurationThreshold())));
        // add filter
        addFilter(new RequestComposer(config));
        // add sink
        addSink(new RequestConfigWriter(config));
        addSink(new RequestJSONWriter(config.getOutputDirectory(), config.getInstanceId()));
    }

    private static Set<Id<TransitStopFacility>> collectTransitStopFacilitiesToKeep(RsschedRequestConfig config) {
        // collect depot locations
        Set<Id<TransitStopFacility>> depotFacilities = config.getDepot()
                .getCapacities()
                .stream()
                .map(facility -> Id.create(facility.locationId(), TransitStopFacility.class))
                .collect(Collectors.toSet());

        // collect maintenance locations
        Set<Id<TransitStopFacility>> maintenanceFacilities = config.getMaintenance()
                .getSlots()
                .stream()
                .map(slot -> Id.create(slot.locationId(), TransitStopFacility.class))
                .collect(Collectors.toSet());

        // merge both sets
        depotFacilities.addAll(maintenanceFacilities);

        return depotFacilities;
    }
}
