package ch.sbb.rssched.client.pipeline.request;

import ch.sbb.rssched.client.config.RsschedRequestConfig;
import ch.sbb.rssched.client.dto.request.Request;
import ch.sbb.rssched.client.pipeline.core.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.VehicleType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Scheduler request composer
 * <p>
 * Constructs a request for the rolling stock scheduler from the results of the scenario and passenger pipeline.
 *
 * @author munterfi
 */
@Log4j2
@RequiredArgsConstructor
public class RequestComposer implements Filter<RequestPipe> {
    public static final int LOCATION_SIZE_LIMIT = 500;
    private final Map<Id<TransitStopFacility>, Boolean> locations = new HashMap<>();
    private final Set<String> departuresIds = new HashSet<>();
    private final Map<TransitStopFacility, Set<VehicleType>> depots = new HashMap<>();

    private final RsschedRequestConfig config;

    private static LocalDateTime toLocalDateTime(double secondsAfterMidnight) {
        final double totalSecondsInDay = 86400.0;

        int daysToAdd = (int) (secondsAfterMidnight / totalSecondsInDay);
        double normalizedSeconds = secondsAfterMidnight % totalSecondsInDay;

        if (normalizedSeconds < 0) {
            normalizedSeconds += totalSecondsInDay;
            daysToAdd--;
        }

        int hours = (int) (normalizedSeconds / 3600);
        int minutes = (int) ((normalizedSeconds % 3600) / 60);
        int seconds = (int) (normalizedSeconds % 60);
        LocalTime time = LocalTime.of(hours, minutes, seconds);

        return LocalDateTime.of(LocalDate.now().plusDays(daysToAdd), time);
    }

    private static Id<VehicleType> getVehicleTypeFrom(RsschedRequestConfig config, TransitLine transitLine) {
        return Id.create(config.getGlobal().getTransitLineVehicleTypeAllocation().get(transitLine.getId().toString()),
                VehicleType.class);
    }

    private static Id<VehicleType> getVehicleTypeFrom(Scenario scenario, TransitRoute transitRoute) {
        Map<Id<VehicleType>, Integer> vehicleTypeFrequencies = new HashMap<>();
        for (Departure departure : transitRoute.getDepartures().values()) {
            var vehicleId = departure.getVehicleId();
            VehicleType vehicleType = scenario.getTransitVehicles().getVehicles().get(vehicleId).getType();
            vehicleTypeFrequencies.put(vehicleType.getId(),
                    vehicleTypeFrequencies.getOrDefault(vehicleType.getId(), 0) + 1);
        }
        Id<VehicleType> mostFrequentVehicleTypeId = null;
        int maxFrequency = -1;
        for (Map.Entry<Id<VehicleType>, Integer> entry : vehicleTypeFrequencies.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                mostFrequentVehicleTypeId = entry.getKey();
                maxFrequency = entry.getValue();
            }
        }
        if (vehicleTypeFrequencies.size() > 1) {
            log.warn("Vehicle types {} are not unique on route {}, returning most frequent type {}",
                    vehicleTypeFrequencies.keySet(), transitRoute.getId(), mostFrequentVehicleTypeId);
        }
        return mostFrequentVehicleTypeId;
    }

    private static List<Segment> collectSegments(TransitRoute transitRoute, Set<String> onRouteLocations) {
        final TransitRouteStop origin = transitRoute.getStops().get(0);
        final TransitRouteStop destination = transitRoute.getStops().get(transitRoute.getStops().size() - 1);

        List<Segment> segments = new ArrayList<>();
        TransitRouteStop currentOrigin = origin;

        for (TransitRouteStop stop : transitRoute.getStops()) {
            if (stop.equals(origin)) {
                continue;
            }
            if (onRouteLocations.contains(stop.getStopFacility().getId().toString()) || stop.equals(destination)) {
                segments.add(new Segment(currentOrigin, stop));
                currentOrigin = stop;
            }
        }
        return segments;
    }

    private static int extractTravelTime(Segment segment) {
        return (int) Math.round(
                segment.destination.getArrivalOffset().seconds() - segment.origin.getDepartureOffset().seconds());
    }

    private static int extractDistance(Network network, TransitRoute transitRoute, Segment segment) {
        Id<Link> fromLink = segment.origin.getStopFacility().getLinkId();
        Id<Link> toLink = segment.destination.getStopFacility().getLinkId();
        double distance = 0.0;

        // Transit route does not contain first and last link of the route, therefore it is checked if the segment is at
        // the beginning of the transit route: From link is not in the link ids, therefore start counting
        boolean countDistance = !transitRoute.getRoute().getLinkIds().contains(fromLink);

        for (Id<Link> linkId : transitRoute.getRoute().getLinkIds()) {
            // only count between the links
            if (linkId.equals(toLink)) {
                break;
            }
            if (countDistance) {
                distance += network.getLinks().get(linkId).getLength();
            }
            if (linkId.equals(fromLink)) {
                countDistance = true;
            }
        }

        return (int) Math.round(distance);
    }

    private static PassengerResult extractPassengers(List<PassengerCount> passengerCounts, Segment segment) {
        int maxPassengers = 0;
        int maxSeats = 0;
        boolean counting = false;

        for (PassengerCount leg : passengerCounts) {
            if (segment.origin.getStopFacility().getId().equals(leg.fromStop().getStopFacility().getId())) {
                counting = true;
            }
            if (counting) {
                maxPassengers = Math.max(maxPassengers, leg.count());
                maxSeats = Math.max(maxSeats, leg.seats());
            }
            if (leg.toStop() != null && segment.destination.getStopFacility()
                    .getId()
                    .equals(leg.toStop().getStopFacility().getId())) {
                break;
            }
        }
        return new PassengerResult(maxPassengers, maxSeats);
    }

    @Override
    public void apply(RequestPipe pipe) {
        setup();
        Scenario scenario = pipe.getScenario();
        Request.Builder builder = Request.builder();
        // compose
        addVehicleTypes(builder, scenario);
        addTransitLines(builder, scenario, pipe.getPassengers());
        if (config.getDepot().isCreateAtTerminalLocations()) {
            if (!config.getDepot().getCapacities().isEmpty()) {
                log.warn(
                        "Specific depots (n = {}) from request config are ignored, since createAtTerminalLocations is set to true",
                        config.getDepot().getCapacities().size());
            }
        } else {
            // only add specific depots from list if automated depot at terminal creation is deactivated.
            // Otherwise, specific depots from config are ignored.
            addDepotsFromConfig(builder, scenario);
        }
        addMaintenanceSlots(builder, scenario);
        addDeadHeadTrips(builder, scenario);
        setParameters(builder);
        // build request
        pipe.setRequest(builder.build());
    }

    private void addVehicleTypes(Request.Builder builder, Scenario scenario) {
        if (config.getGlobal().getVehicleTypes().isEmpty()) {
            addVehicleTypesFromScenario(builder, scenario);
        } else {
            addVehicleTypesFromConfig(builder);
        }
    }

    private void addVehicleTypesFromConfig(Request.Builder builder) {
        config.getGlobal()
                .getVehicleTypes()
                .forEach(vehicleType -> builder.addVehicleType(vehicleType.id(), vehicleType.capacity(),
                        vehicleType.seats(), vehicleType.maximalFormationCount()));

    }

    private void addVehicleTypesFromScenario(Request.Builder builder, Scenario scenario) {
        scenario.getTransitVehicles()
                .getVehicleTypes()
                .forEach((vehicleTypeId, vehicleType) -> builder.addVehicleType(vehicleTypeId.toString(),
                        vehicleType.getCapacity().getSeats(),
                        vehicleType.getCapacity().getSeats() + vehicleType.getCapacity().getStandingRoom(),
                        config.getShunting().getDefaultMaximalFormationCount()));
    }

    private void setup() {
        depots.clear();
        departuresIds.clear();
        locations.clear();
    }

    private void addDepotsToTerminalLocation(Request.Builder builder, Scenario scenario, TransitRoute transitRoute) {
        TransitStopFacility origin = transitRoute.getStops().get(0).getStopFacility();
        addDepot(builder, origin);
        // add upper bound for vehicle types
        for (Departure departure : transitRoute.getDepartures().values()) {
            VehicleType vehicleType = scenario.getTransitVehicles()
                    .getVehicles()
                    .get(departure.getVehicleId())
                    .getType();
            Set<VehicleType> bounds = depots.computeIfAbsent(origin, k -> new HashSet<>());
            if (!bounds.contains(vehicleType)) {
                builder.addVehicleTypeToDepot(config.getDepot().getDefaultIdPrefix() + origin.getId(),
                        vehicleType.getId().toString(), config.getDepot().getDefaultCapacity());
                bounds.add(vehicleType);
            }
        }
    }

    private void addRouteWithDepartures(Request.Builder builder, Scenario scenario, TransitLine transitLine, TransitRoute transitRoute, Map<Id<Departure>, List<PassengerCount>> passengers) {
        final String transitRouteId = transitRoute.getId().toString();
        final List<Segment> segments = collectSegments(transitRoute, config.getShunting().getOnRouteLocations());

        if (config.getGlobal().getTransitLineVehicleTypeAllocation().isEmpty()) {
            builder.addRoute(transitRouteId, getVehicleTypeFrom(scenario, transitRoute).toString());
        } else {
            builder.addRoute(transitRouteId, getVehicleTypeFrom(config, transitLine).toString());
        }

        // add intermediate locations and route segments
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            log.debug("Adding segment {} - {} on {}", segment.origin, segment.destination, transitRoute);
            String segmentId = String.format("%s_%d", transitRoute.getId(), i);
            // add intermediate location
            addLocation(builder, segment.origin.getStopFacility());
            addLocation(builder, segment.destination.getStopFacility());
            // add route segment
            builder.addSegmentToRoute(segmentId, transitRouteId, segment.origin.getStopFacility().getId().toString(),
                    segment.destination.getStopFacility().getId().toString(),
                    extractDistance(scenario.getNetwork(), transitRoute, segment), extractTravelTime(segment),
                    config.getShunting().getDefaultMaximalFormationCount());
            // add route departures
            for (Departure departure : transitRoute.getDepartures().values()) {
                String departureId = String.format("%s_%s", transitRouteId, departure.getId());
                if (!departuresIds.contains(departureId)) {
                    builder.addDeparture(departureId, transitRouteId);
                    departuresIds.add(departureId);
                }
                String departureSegmentId = String.format("%s_%d", departureId, i);
                // add departure segment to route segment
                double departureTime = departure.getDepartureTime();
                if (i > 0) {
                    // adjust departure time if not first segment
                    departureTime += segment.origin.getDepartureOffset().seconds();
                }
                // get total and seated passengers
                PassengerResult result = extractPassengers(passengers.get(departure.getId()), segment);
                // multiply with capacity factor to reflect deviations in passenger demand
                builder.addSegmentToDeparture(departureSegmentId, departureId, segmentId,
                        toLocalDateTime(departureTime),
                        (int) Math.round(result.passengers * config.getGlobal().getCapacityFactor()),
                        (int) Math.round(result.seats * config.getGlobal().getCapacityFactor()));
            }
        }
    }

    private void addTransitLines(Request.Builder builder, Scenario scenario, Map<Id<TransitLine>, Map<Id<TransitRoute>, Map<Id<Departure>, List<PassengerCount>>>> passengers) {
        for (TransitLine transitLine : scenario.getTransitSchedule().getTransitLines().values()) {
            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                addRouteWithDepartures(builder, scenario, transitLine, transitRoute,
                        passengers.get(transitLine.getId()).get(transitRoute.getId()));
                if (config.getDepot().isCreateAtTerminalLocations()) {
                    addDepotsToTerminalLocation(builder, scenario, transitRoute);
                }
            }
        }
    }

    private void addLocation(Request.Builder builder, TransitStopFacility facility) {
        String facilityId = facility.getId().toString();
        if (!locations.containsKey(facility.getId())) {
            builder.addLocation(facilityId);
            locations.put(facility.getId(), false);
        }
    }

    private void addDepot(Request.Builder builder, TransitStopFacility facility) {
        String facilityId = facility.getId().toString();
        Boolean existingDepot = locations.get(facility.getId());

        if (existingDepot == null) {
            throw new IllegalArgumentException("Location " + facilityId + "not found.");
        }

        if (!existingDepot) {
            String depotId = config.getDepot().getDefaultIdPrefix() + facility.getId().toString();
            builder.addDepot(depotId, facilityId, config.getDepot().getDefaultCapacity());
            locations.put(facility.getId(), true);
        } else {
            log.debug("A depot already exists at location {} - Skipping", facilityId);
        }
    }

    private void addDepotsFromConfig(Request.Builder builder, Scenario scenario) {
        for (RsschedRequestConfig.Depot.Facility capacity : config.getDepot().getCapacities()) {

            // add depot location if not yet existing
            Id<TransitStopFacility> facilityId = Id.create(capacity.locationId(), TransitStopFacility.class);
            TransitStopFacility facility = scenario.getTransitSchedule().getFacilities().get(facilityId);
            if (facility == null) {
                throw new IllegalStateException(
                        "Depot location " + facilityId + " not found in transit schedule facilities.");
            }
            addLocation(builder, facility);

            // add depot with allowed type capacities
            builder.addDepot(capacity.id(), capacity.locationId(), capacity.capacity());
            for (RsschedRequestConfig.Depot.AllowedType allowedType : capacity.allowedTypes()) {
                builder.addVehicleTypeToDepot(capacity.id(), allowedType.vehicleType(), allowedType.capacity());
            }
        }
    }

    private void addMaintenanceSlots(Request.Builder builder, Scenario scenario) {
        for (RsschedRequestConfig.Maintenance.Slot slot : config.getMaintenance().getSlots()) {

            // add maintenance location if not yet existing
            Id<TransitStopFacility> facilityId = Id.create(slot.locationId(), TransitStopFacility.class);
            TransitStopFacility facility = scenario.getTransitSchedule().getFacilities().get(facilityId);
            if (facility == null) {
                throw new IllegalStateException(
                        "Maintenance location " + facilityId + " not found in transit schedule facilities.");
            }
            addLocation(builder, facility);

            // add maintenance slot with number of tracks
            builder.addMaintenanceSlot(slot.id(), slot.locationId(), slot.start(), slot.end(), slot.trackCount());
        }
    }

    private void addDeadHeadTrips(Request.Builder builder, Scenario scenario) {
        if (locations.keySet().size() > LOCATION_SIZE_LIMIT) {
            throw new IllegalStateException(
                    "Instance is to big for creating deadhead trip matrix, there have to be less than " + LOCATION_SIZE_LIMIT + " locations.");
        }
        log.info("Creating dead head trip matrix ({}x{}={})", locations.keySet().size(), locations.keySet().size(),
                locations.keySet().size() * locations.keySet().size());
        TrainNetworkRouter trainNetworkRouter = new TrainNetworkRouter(scenario.getNetwork(),
                config.getGlobal().getDeadHeadTripSpeedLimit(),
                config.getGlobal().getDeadHeadTripBeelineDistanceFactor());
        locations.keySet().forEach(originId -> locations.keySet().forEach(destinationId -> {
            if (!originId.equals(destinationId)) {
                TrainNetworkRouter.PathResult pathResult = trainNetworkRouter.calculate(
                        scenario.getTransitSchedule().getFacilities().get(originId),
                        scenario.getTransitSchedule().getFacilities().get(destinationId));
                builder.addDeadHeadTrip(originId.toString(), destinationId.toString(), pathResult.duration(),
                        pathResult.distance());
            }
        }));
    }

    private void setParameters(Request.Builder builder) {
        RsschedRequestConfig.Shunting shunting = config.getShunting();
        builder.setShuntingParameters(shunting.getMinimalDuration(), shunting.getDeadHeadTripDuration(),
                shunting.getCouplingDuration());
        builder.setMaintenanceParameters(config.getMaintenance().getMaximalDistance());
        RsschedRequestConfig.Costs costs = config.getCosts();
        builder.setCostParameters(costs.getStaff(), costs.getServiceTrip(), costs.getMaintenance(),
                costs.getDeadHeadTrip(), costs.getIdle());
        RsschedRequestConfig.Global global = config.getGlobal();
        builder.setGlobalParameters(global.isForbidDeadHeadTrips(), global.getDayLimitThreshold());
    }

    public record PassengerResult(int passengers, int seats) {
    }

    /**
     * Helper class to store route segments
     *
     * @param origin      origin location
     * @param destination destination location
     */
    record Segment(TransitRouteStop origin, TransitRouteStop destination) {
    }

    public record PassengerCount(TransitRouteStop fromStop, TransitRouteStop toStop, int count, int seats) {
    }
}
