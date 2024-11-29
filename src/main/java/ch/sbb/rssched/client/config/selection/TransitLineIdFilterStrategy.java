package ch.sbb.rssched.client.config.selection;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitLine;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TransitLineIdFilterStrategy implements FilterStrategy {

    private final Map<String, String> transitLineVehicleTypeAllocations;
    private final Map<String, String> lookup = new HashMap<>();

    public TransitLineIdFilterStrategy(Map<String, String> transitLineVehicleTypeAllocations, Set<VehicleCategory> categories) {
        this.transitLineVehicleTypeAllocations = transitLineVehicleTypeAllocations;
        categories.forEach(category -> category.vehicleTypes().forEach(type -> lookup.put(type, category.group())));
    }

    @Override
    public TransitLineSelection filter(Scenario scenario) {
        var selection = new TransitLineSelection();

        for (var transitLineId : transitLineVehicleTypeAllocations.keySet()) {
            var transitLine = scenario.getTransitSchedule()
                    .getTransitLines()
                    .get(Id.create(transitLineId, TransitLine.class));

            if (transitLine == null) {
                throw new IllegalArgumentException(
                        String.format("No transit line found in scenario with id %s", transitLineId));
            }

            transitLine.getRoutes().forEach((transitRouteId, transitRoute) -> {
                selection.add(lookup.get(transitLineVehicleTypeAllocations.get(transitLine.getId().toString())),
                        transitLine.getId(), transitRouteId);
            });

        }

        return selection;
    }

}
