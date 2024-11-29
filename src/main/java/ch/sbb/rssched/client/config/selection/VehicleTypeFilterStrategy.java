package ch.sbb.rssched.client.config.selection;

import lombok.ToString;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.vehicles.VehicleType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Transit vehicle type filter
 * <p>
 * Filters a scenario based on transit vehicle types and assigns them to groups.
 *
 * @author munterfi
 */
@ToString
public class VehicleTypeFilterStrategy implements FilterStrategy {

    private final Map<String, String> lookup = new HashMap<>();

    public VehicleTypeFilterStrategy(Set<VehicleCategory> categories) {
        categories.forEach(category -> category.vehicleTypes().forEach(type -> lookup.put(type, category.group())));
    }

    private static Id<VehicleType> getAndEnsureConsistentVehicleType(Scenario scenario, TransitRoute transitRoute) {
        var departures = transitRoute.getDepartures().values();
        var firstVehicleTypeId = departures.stream()
                .findFirst()
                .map(departure -> scenario.getTransitVehicles()
                        .getVehicles()
                        .get(departure.getVehicleId())
                        .getType()
                        .getId())
                .orElseThrow(() -> new RuntimeException("No departures found for the route"));
        departures.stream()
                .map(departure -> scenario.getTransitVehicles()
                        .getVehicles()
                        .get(departure.getVehicleId())
                        .getType()
                        .getId())
                .forEach(vehicleTypeId -> {
                    if (!vehicleTypeId.equals(firstVehicleTypeId)) {
                        throw new RuntimeException("Inconsistent vehicle types found in route departures");
                    }
                });
        return firstVehicleTypeId;
    }

    @Override
    public TransitLineSelection filter(Scenario scenario) {
        var selection = new TransitLineSelection();
        scenario.getTransitSchedule()
                .getTransitLines()
                .forEach((transitLineId, transitLine) -> transitLine.getRoutes()
                        .forEach((transitRouteId, transitRoute) -> {
                            String vehicleTypeId = getAndEnsureConsistentVehicleType(scenario, transitRoute).toString();
                            if (lookup.containsKey(vehicleTypeId)) {
                                selection.add(lookup.get(vehicleTypeId), transitLineId, transitRouteId);
                            }
                        }));
        return selection;
    }

}
