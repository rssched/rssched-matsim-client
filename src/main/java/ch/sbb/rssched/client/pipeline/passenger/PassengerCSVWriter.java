package ch.sbb.rssched.client.pipeline.passenger;

import ch.sbb.rssched.client.pipeline.core.ResultSink;
import ch.sbb.rssched.client.pipeline.utils.io.OutputDirectoryManager;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Exports passenger data to a CSV file in the specified output directory.
 * <p>
 * The passenger data includes information such as transit line ID, transit route ID, departure ID, stop ID, stop name,
 * arrival time, departure time, egress time, access time, destination stop ID, destination stop name, and passenger
 * count.
 *
 * @author munterfi
 * @see EventAnalysis.Entry
 */
@Log4j2
class PassengerCSVWriter implements ResultSink<PassengerPipe> {
    private static final String PASSENGER_FILE = "passenger.csv";
    private static final String[] HEADER = {"transit_line_id", "transit_route_id", "departure_id", "stop_id", "stop_name", "arrival", "departure", "egress", "access", "to_stop_id", "to_stop_name", "passenger_demand", "seat_demand", "passenger_min_capacity", "seat_min_capacity"};
    private final String outputDirectory;
    private final String instanceId;
    private final double capacityFactor;

    /**
     * Constructs a PassengerExporter with the specified output directory.
     *
     * @param outputDirectory the directory to export the passenger file to.
     */
    public PassengerCSVWriter(String outputDirectory, String instanceId, double capacityFactor) {
        this.outputDirectory = outputDirectory;
        this.instanceId = instanceId;
        this.capacityFactor = capacityFactor;
    }

    public static void writeCsv(List<EventAnalysis.Entry> entries, String filename, double capacityFactor) throws UncheckedIOException {
        try (CSVPrinter csv = new CSVPrinter(IOUtils.getBufferedWriter(filename),
                CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            for (var entry : entries) {
                double routeDepartureTime = entry.getDeparture().getDepartureTime();
                String arrivalTime = "";
                String departureTime = "";
                String toStopId = "";
                String toStopName = "";
                // check if not at terminal station
                if (entry.getToStop() != null) {
                    double departureOffset = entry.getFromStop().getDepartureOffset().seconds();
                    departureTime = Time.writeTime(routeDepartureTime + departureOffset, Time.TIMEFORMAT_HHMMSS);
                    toStopId = entry.getToStop().getStopFacility().getId().toString();
                    toStopName = entry.getToStop().getStopFacility().getName();
                }
                // check if not at origin / first stop of route
                if (entry.getFromStop().getArrivalOffset().isDefined()) {
                    double arrivalOffset = entry.getFromStop().getArrivalOffset().seconds();
                    arrivalTime = Time.writeTime(routeDepartureTime + arrivalOffset, Time.TIMEFORMAT_HHMMSS);
                }
                csv.print(entry.getTransitLine().getId().toString());
                csv.print(entry.getTransitRoute().getId().toString());
                csv.print(entry.getDeparture().getId().toString());
                csv.print(entry.getFromStop().getStopFacility().getId().toString());
                csv.print(entry.getFromStop().getStopFacility().getName());
                csv.print(arrivalTime);
                csv.print(departureTime);
                csv.print(entry.getEgress());
                csv.print(entry.getAccess());
                csv.print(toStopId);
                csv.print(toStopName);
                csv.print(entry.getCount());
                csv.print(entry.getSeats());
                csv.print(Math.round(entry.getCount() * capacityFactor));
                csv.print(Math.round(entry.getSeats() * capacityFactor));
                csv.println();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void process(PassengerPipe pipe) {
        String passengerFilePath = new OutputDirectoryManager(outputDirectory, pipe.runId(), instanceId).buildFilePath(
                PASSENGER_FILE);
        log.info("Exporting passenger file to {}", passengerFilePath);
        writeCsv(pipe.entries(), passengerFilePath, capacityFactor);
    }
}
