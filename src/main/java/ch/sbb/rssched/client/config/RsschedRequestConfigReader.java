package ch.sbb.rssched.client.config;

import ch.sbb.rssched.client.config.selection.TransitLineIdFilterStrategy;
import ch.sbb.rssched.client.config.selection.VehicleCategory;
import ch.sbb.rssched.client.config.selection.VehicleTypeFilterStrategy;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reader for XLSX configuration
 *
 * @author munterfi
 */
@NoArgsConstructor
@Log4j2
public class RsschedRequestConfigReader {

    public static final String DEPOT_UNDEFINED_VEHICLE_TYPE = "TOTAL";
    private final RsschedRequestConfig.Builder builder = RsschedRequestConfig.builder();
    private final Set<String> allVehicleTypes = new HashSet<>();

    private static void checkIfSheetExists(Sheet sheet, String sheetName) {
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet " + sheetName + " not found");
        }
    }

    public RsschedRequestConfig readExcelFile(String filePath) throws IOException {
        FileInputStream fis = new FileInputStream(filePath);
        Workbook workbook = new XSSFWorkbook(fis);
        parseScenarioInfoSheet(workbook.getSheet(Sheets.SCENARIO_INFO));
        parseTransitLinesSheet(workbook.getSheet(Sheets.TRANSIT_LINES));
        parseVehicleTypesSheet(workbook.getSheet(Sheets.VEHICLE_TYPES));
        parseShuntingLocationsOnRouteSheet(workbook.getSheet(Sheets.SHUNTING_LOCATIONS_ON_ROUTE));
        parseDepotLocationsSheet(workbook.getSheet(Sheets.DEPOT_LOCATIONS));
        parseMaintenanceSlotsSheet(workbook.getSheet(Sheets.MAINTENANCE_SLOTS));
        return builder.buildWithDefaults();
    }

    private void parseTransitLinesSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.TRANSIT_LINES);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header row
            Cell transitLineIdCell = row.getCell(0);
            Cell vehicleTypeIdCell = row.getCell(1);
            if (transitLineIdCell != null && vehicleTypeIdCell != null) {
                String transitLineId = transitLineIdCell.getStringCellValue();
                String vehicleTypeId = vehicleTypeIdCell.getStringCellValue();
                builder.config.getGlobal().getTransitLineVehicleTypeAllocation().put(transitLineId, vehicleTypeId);
            } else {
                throw new IllegalStateException("Incomplete transit line row.");
            }
        }
    }

    private void parseScenarioInfoSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.SCENARIO_INFO);

        for (Row row : sheet) {
            Cell groupCell = row.getCell(0);
            Cell parameterCell = row.getCell(1);
            Cell valueCell = row.getCell(2);

            if (groupCell != null && parameterCell != null && valueCell != null) {
                String group = groupCell.getStringCellValue();
                String parameter = parameterCell.getStringCellValue();

                switch (group) {
                    case "global":
                        switch (parameter) {
                            case "instanceId":
                                String instanceId = valueCell.getStringCellValue();
                                builder.setInstanceId(instanceId);
                                break;
                            case "matsimRunId":
                                builder.setRunId(valueCell.getStringCellValue());
                                break;
                            case "matsimInputDirectory":
                                builder.setInputDirectory(valueCell.getStringCellValue());
                                break;
                            case "outputDirectory":
                                builder.setOutputDirectory(valueCell.getStringCellValue());
                                break;
                            case "networkCrs":
                                builder.setNetworkCrs(valueCell.getStringCellValue());
                                break;
                            case "sampleSize":
                                builder.config.getGlobal().setSampleSize(valueCell.getNumericCellValue());
                                break;
                            case "deadHeadTripSpeedLimit":
                                builder.config.getGlobal().setDeadHeadTripSpeedLimit(valueCell.getNumericCellValue());
                                break;
                            case "deadHeadTripBeelineDistanceFactor":
                                builder.config.getGlobal()
                                        .setDeadHeadTripBeelineDistanceFactor(valueCell.getNumericCellValue());
                                break;
                            case "forbidDeadHeadTrips":
                                builder.config.getGlobal().setForbidDeadHeadTrips(valueCell.getBooleanCellValue());
                                break;
                            case "dayLimitThreshold":
                                builder.config.getGlobal().setDayLimitThreshold((int) valueCell.getNumericCellValue());
                                break;
                            case "seatDurationThreshold":
                                builder.config.getGlobal()
                                        .setSeatDurationThreshold((int) valueCell.getNumericCellValue());
                                break;
                        }
                        break;
                    case "depot":
                        switch (parameter) {
                            case "defaultCapacity":
                                builder.config.getDepot().setDefaultCapacity((int) valueCell.getNumericCellValue());
                                break;
                            case "defaultIdPrefix":
                                builder.config.getDepot().setDefaultIdPrefix(valueCell.getStringCellValue());
                                break;
                            case "createAtTerminalLocations":
                                builder.config.getDepot().setCreateAtTerminalLocations(valueCell.getBooleanCellValue());
                                break;
                        }
                        break;
                    case "shunting":
                        int value = (int) valueCell.getNumericCellValue();
                        switch (parameter) {
                            case "defaultMaximalFormationCount":
                                builder.config.getShunting().setDefaultMaximalFormationCount(value);
                                break;
                            case "minimalDuration":
                                builder.config.getShunting().setMinimalDuration(value);
                                break;
                            case "deadHeadTripDuration":
                                builder.config.getShunting().setDeadHeadTripDuration(value);
                                break;
                            case "couplingDuration":
                                builder.config.getShunting().setCouplingDuration(value);
                                break;
                        }
                        break;
                    case "maintenance":
                        if (parameter.equals("maximalDistance")) {
                            builder.config.getMaintenance().setMaximalDistance((int) valueCell.getNumericCellValue());
                        }
                        break;
                    case "costs":
                        int cost = (int) valueCell.getNumericCellValue();
                        switch (parameter) {
                            case "staff":
                                builder.config.getCosts().setStaff(cost);
                                break;
                            case "idle":
                                builder.config.getCosts().setIdle(cost);
                                break;
                            case "serviceTrip":
                                builder.config.getCosts().setServiceTrip(cost);
                                break;
                            case "deadHeadTrip":
                                builder.config.getCosts().setDeadHeadTrip(cost);
                                break;
                            case "maintenance":
                                builder.config.getCosts().setMaintenance(cost);
                                break;
                        }
                        break;
                }
            } else {
                throw new IllegalStateException("Incomplete scenario info row.");
            }
        }
    }

    private void parseVehicleTypesSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.VEHICLE_TYPES);

        Map<String, Set<String>> vehicleTypesPerGroup = new HashMap<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header row
            Cell groupIdCell = row.getCell(0);
            Cell vehicleTypeIdCell = row.getCell(1);
            Cell standingRoomCell = row.getCell(2);
            Cell seatsCell = row.getCell(3);
            Cell maximumFormationCountCell = row.getCell(4);

            if (groupIdCell != null && vehicleTypeIdCell != null && standingRoomCell != null && seatsCell != null && maximumFormationCountCell != null) {
                String groupId = groupIdCell.getStringCellValue();
                String vehicleTypeId = vehicleTypeIdCell.getStringCellValue();
                int standingRoom = (int) standingRoomCell.getNumericCellValue();
                int seats = (int) seatsCell.getNumericCellValue();
                int maximumFormationCount = (int) maximumFormationCountCell.getNumericCellValue();

                // store type for generic depots, which have to support all types
                allVehicleTypes.add(vehicleTypeId);

                // add to group for vehicle type filter strategy
                Set<String> group = vehicleTypesPerGroup.computeIfAbsent(groupId, ignored -> new HashSet<>());
                group.add(vehicleTypeId);

                // add vehicle as vehicle type, will overwrite MATSim transit vehicle values
                builder.config.getGlobal()
                        .getVehicleTypes()
                        .add(new RsschedRequestConfig.Global.VehicleType(vehicleTypeId, standingRoom + seats, seats,
                                maximumFormationCount));
            } else {
                throw new IllegalStateException("Incomplete vehicle type row.");
            }
        }

        // add filter strategy for vehicle types
        Set<VehicleCategory> vehicleCategories = new HashSet<>();
        for (var group : vehicleTypesPerGroup.entrySet()) {
            vehicleCategories.add(new VehicleCategory(group.getKey(), group.getValue()));
        }

        if (builder.config.getGlobal().getTransitLineVehicleTypeAllocation().isEmpty()) {
            builder.setFilterStrategy(new VehicleTypeFilterStrategy(vehicleCategories));
        } else {

            // ensure all allocated vehicle types in transit line sheet exist in the vehicle type sheet
            Set<String> existingVehicleTypeIds = builder.config.getGlobal()
                    .getVehicleTypes()
                    .stream()
                    .map(RsschedRequestConfig.Global.VehicleType::id)
                    .collect(Collectors.toSet());

            for (var vehicleTypeId : new HashSet<>(
                    builder.config.getGlobal().getTransitLineVehicleTypeAllocation().values())) {
                if (!existingVehicleTypeIds.contains(vehicleTypeId)) {
                    throw new IllegalStateException(
                            "Vehicle type " + vehicleTypeId + " from transit line selection is missing in vehicle type sheet.");
                }
            }

            builder.setFilterStrategy(
                    new TransitLineIdFilterStrategy(builder.config.getGlobal().getTransitLineVehicleTypeAllocation(),
                            vehicleCategories));
        }
    }

    private void parseShuntingLocationsOnRouteSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.SHUNTING_LOCATIONS_ON_ROUTE);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header row
            Cell locationIdCell = row.getCell(0);
            if (locationIdCell != null) {
                String locationId = locationIdCell.getStringCellValue();
                builder.addShuntingLocation(locationId);
            } else {
                throw new IllegalStateException("Incomplete shunting location row.");
            }
        }
    }

    private void parseDepotLocationsSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.DEPOT_LOCATIONS);

        Map<String, Map<String, Integer>> depotCapacities = new HashMap<>();

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header row
            Cell locationIdCell = row.getCell(0);
            Cell vehicleTypeIdCell = row.getCell(1);
            Cell capacityCell = row.getCell(2);

            if (locationIdCell != null && vehicleTypeIdCell != null && capacityCell != null) {
                String locationId = locationIdCell.getStringCellValue();
                String vehicleTypeId = vehicleTypeIdCell.getStringCellValue();
                int capacity = (int) capacityCell.getNumericCellValue();

                Map<String, Integer> allowedType = depotCapacities.computeIfAbsent(locationId,
                        ignored -> new HashMap<>());
                allowedType.put(vehicleTypeId, capacity);
            } else {
                throw new IllegalStateException("Incomplete depot location row.");
            }
        }

        for (Map.Entry<String, Map<String, Integer>> entry : depotCapacities.entrySet()) {
            Map<String, Integer> allowedTypes = entry.getValue();
            String locationId = entry.getKey();
            String depotId = builder.config.getDepot().getDefaultIdPrefix() + locationId;

            if (allowedTypes.containsKey(DEPOT_UNDEFINED_VEHICLE_TYPE)) {
                if (allowedTypes.size() > 1) {
                    throw new IllegalArgumentException(
                            "Specific vehicle types in cannot be specified for generic depot: " + depotId);
                }

                // add generic depot with total capacity
                log.debug("Depot {} at location {} is generic ({})", depotId, locationId, DEPOT_UNDEFINED_VEHICLE_TYPE);
                int totalCapacity = allowedTypes.get(DEPOT_UNDEFINED_VEHICLE_TYPE);
                builder.addDepot(depotId, locationId, totalCapacity);

                // add allowed types for all vehicle types to depot
                for (String vehicleTypeId : allVehicleTypes) {
                    log.debug("Add vehicle type {} with total capacity {} to depot {}", vehicleTypeId, totalCapacity,
                            depotId);
                    builder.addAllowedTypeToDepot(depotId, vehicleTypeId, totalCapacity);
                }

            } else {
                // add depot to location
                int totalCapacity = allowedTypes.values().stream().mapToInt(Integer::intValue).sum();
                builder.addDepot(depotId, locationId, totalCapacity);

                // add allowed types to depot
                for (Map.Entry<String, Integer> allowedType : allowedTypes.entrySet()) {
                    String vehicleTypeId = allowedType.getKey();
                    int capacity = allowedType.getValue();
                    log.debug("Add vehicle type {} with capacity {} to depot {}", vehicleTypeId, capacity, depotId);
                    builder.addAllowedTypeToDepot(depotId, vehicleTypeId, capacity);
                }
            }
        }
    }

    private void parseMaintenanceSlotsSheet(Sheet sheet) {
        checkIfSheetExists(sheet, Sheets.MAINTENANCE_SLOTS);

        int count = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // skip header row
            Cell locationIdCell = row.getCell(0);
            Cell tracksCell = row.getCell(1);
            Cell fromCell = row.getCell(2);
            Cell toCell = row.getCell(3);

            if (locationIdCell != null && tracksCell != null && fromCell != null && toCell != null) {
                String locationId = locationIdCell.getStringCellValue();
                int tracks = (int) tracksCell.getNumericCellValue();
                LocalTime fromTime = LocalTime.ofSecondOfDay((long) (fromCell.getNumericCellValue() * 86400));
                LocalTime toTime = LocalTime.ofSecondOfDay((long) (toCell.getNumericCellValue() * 86400));
                LocalDateTime start = LocalDateTime.of(LocalDateTime.now().toLocalDate(), fromTime);
                LocalDateTime end = LocalDateTime.of(LocalDateTime.now().toLocalDate(), toTime);

                // check if the end time is before the start time, indicating an overnight slot
                if (toTime.isBefore(fromTime)) {
                    end = end.plusDays(1);
                }

                String maintenanceSlotId = String.format("%s_%d_%d", locationId, tracks, ++count);
                log.debug("Add maintenance slot {} at location {} with {} tracks from {} to {}", maintenanceSlotId,
                        locationId, tracks, start, end);
                builder.addMaintenanceSlot(maintenanceSlotId, locationId, start, end, tracks);
            } else {
                throw new IllegalStateException("Incomplete maintenance slot row.");
            }
        }
    }

    private static final class Sheets {
        public static final String DEPOT_LOCATIONS = "depot_locations";
        public static final String MAINTENANCE_SLOTS = "maintenance_slots";
        public static final String SHUNTING_LOCATIONS_ON_ROUTE = "shunting_locations_on_route";
        public static final String VEHICLE_TYPES = "vehicle_types";
        public static final String TRANSIT_LINES = "transit_lines";
        public static final String SCENARIO_INFO = "scenario_info";
    }
}
