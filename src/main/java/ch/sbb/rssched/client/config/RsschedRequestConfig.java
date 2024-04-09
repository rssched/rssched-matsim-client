package ch.sbb.rssched.client.config;

import ch.sbb.rssched.client.config.selection.FilterStrategy;
import ch.sbb.rssched.client.config.selection.NoFilterStrategy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configure pipeline and request config
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class RsschedRequestConfig {
    private final Global global = new Global();
    private final Depot depot = new Depot();
    private final Shunting shunting = new Shunting();
    private final Maintenance maintenance = new Maintenance();
    private final Costs costs = new Costs();
    private String runId;
    private String inputDirectory;
    private String outputDirectory;

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing a RequestConfig instance with customized settings. Allows addition of depots,
     * shunting locations, and maintenance slots.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {
        private final Set<String> depotLocations = new HashSet<>();
        private final Map<String, Depot.Facility> depots = new HashMap<>();
        private final RsschedRequestConfig config = new RsschedRequestConfig();

        public Builder setRunId(String runId) {
            config.runId = runId;
            return this;
        }

        public Builder setInputDirectory(String inputDirectory) {
            config.inputDirectory = inputDirectory;
            return this;
        }

        public Builder setOutputDirectory(String outputDirectory) {
            config.outputDirectory = outputDirectory;
            return this;
        }

        public Builder setFilterStrategy(FilterStrategy filterStrategy) {
            config.global.filterStrategy = filterStrategy;
            return this;
        }

        public Builder addDepot(String id, String locationId, int capacity) {
            if (depots.containsKey(id)) {
                throw new IllegalArgumentException("Depot " + id + "already exists.");
            }
            if (depotLocations.contains(locationId)) {
                throw new IllegalArgumentException("Depot already exists at location " + locationId);
            }
            Depot.Facility facility = new Depot.Facility(id, locationId, capacity);
            depotLocations.add(locationId);
            depots.put(id, facility);
            config.depot.capacities.add(facility);
            return this;
        }

        public Builder addAllowedTypeToDepot(String id, String vehicleTypeId, int capacity) {
            Depot.Facility facility = depots.get(id);
            if (facility == null) {
                throw new IllegalArgumentException("Depot " + id + "does not exist.");
            }
            facility.allowedTypes.add(new Depot.AllowedType(vehicleTypeId, capacity));
            return this;
        }

        public Builder addShuntingLocation(String locationId) {
            if (config.shunting.onRouteLocations.contains(locationId)) {
                throw new IllegalArgumentException("Shunting location with id" + locationId + "already exists.");
            }
            config.shunting.onRouteLocations.add(locationId);
            return this;
        }

        public Builder addMaintenanceSlot(String id, String locationId, LocalDateTime start, LocalDateTime end) {
            Maintenance.Slot slot = new Maintenance.Slot(id, locationId, start, end);
            config.maintenance.slots.add(slot);
            return this;
        }

        /**
         * Builds the RequestConfig with all added configurations and defaults, which can be adjusted if needed.
         *
         * @return The fully configured RequestConfig instance.
         */
        public RsschedRequestConfig buildWithDefaults() {
            if (config.runId == null || config.inputDirectory == null || config.outputDirectory == null) {
                throw new IllegalStateException(
                        "Mandatory fields (runId, inputDirectory, outputDirectory) must be set.");
            }
            return config;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Global {

        /**
         * The filter strategy to filter transit lines of interest, default is no filter.
         */
        private FilterStrategy filterStrategy = new NoFilterStrategy();

        /**
         * Speed limit used in the routing for deadhead trips
         */
        private double deadHeadTripSpeedLimit = 90 * 3.6;

        /**
         * Allow deadhead trips?
         */
        private boolean forbidDeadHeadTrips = false;

        /**
         * Vehicles with stopping times under this threshold in seconds do not count into dayLimit at stations.
         */
        private int dayLimitThreshold = 0;

        /**
         * Passenger travelling longer than this threshold are assigned a seat. Travel times below this threshold
         * increase the total passenger count, but not the seat count.
         */
        private int seatDurationThreshold = 15 * 60;

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Depot {

        /**
         * Optional capacities per depot location, this will create a depot and set its capacity.
         */
        private final List<Facility> capacities = new ArrayList<>();

        /**
         * The default capacity that is assigned to depots in case of automated depot creation.
         */
        private int defaultCapacity = 999;

        /**
         * The prefix that is to the location id to create a depot id in case of automated depot creation.
         */
        private String defaultIdPrefix = "dpt_";

        /**
         * Automatically create depots at terminal locations?
         */
        private boolean createAtTerminalLocations = true;

        public record Facility(String id, String locationId, int capacity, List<AllowedType> allowedTypes) {
            public Facility(String id, String locationId, int capacity) {
                this(id, locationId, capacity, new ArrayList<>());
            }

            public Facility {
                allowedTypes = allowedTypes != null ? allowedTypes : new ArrayList<>();
            }

        }

        public record AllowedType(String vehicleType, int capacity) {
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Shunting {

        /**
         * Locations along the route where train units can be added to or removed from the formation without changing
         * the lead vehicle.
         */
        private final Set<String> onRouteLocations = new HashSet<>();

        /**
         * The default maximal number oof units in a formation.
         */
        private int defaultMaximalFormationCount = 3;

        /**
         * The minimum time that is always needed between two activities in seconds.
         */
        private int minimalDuration = 60;

        /**
         * The time in seconds to change from serviceTrip to DeadHeadTrip.
         */
        private int deadHeadTripDuration = 2 * 60;

        /**
         * Additional time needed for a vehicle to be coupled or uncoupled and changes route in seconds.
         */
        private int couplingDuration = 3 * 60;

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Maintenance {

        /**
         * Optional maintenance slots that can be used by units at existing locations in the network.
         */
        private final List<Slot> slots = new ArrayList<>();

        /**
         * The maximal distance a vehicle can travel without maintenance in meters.
         */
        private int maximalDistance = 15000 * 1000;

        public record Slot(String id, String locationId, LocalDateTime start, LocalDateTime end) {
        }
    }

    /**
     * Costs are always per second.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    @Setter
    public static class Costs {

        /**
         * Each train formation on a service trip has to pay this per minute (not for dead-head-trips / idle /
         * maintenance).
         */
        private int staff = 100;

        /**
         * Train formation with k vehicles has to pay this k times per second when idle.
         */
        private int idle = 25;

        /**
         * Train formation with k vehicles has to pay this k times per second on a service trip.
         */
        private int serviceTrip = 50;

        /**
         * Train formation with k vehicles has to pay this k times per second on a deadhead trip.
         */
        private int deadHeadTrip = 75;

        /**
         * Maintenance costs are also positive.
         */
        private int maintenance = 200;

    }
}