/* amodeus - Copyright (c) 2018, ETH Zurich, Institute for Dynamic Systems and Control */
package amod.scenario.readers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.ethz.idsc.amodeus.taxitrip.TaxiTrip;
import ch.ethz.idsc.amodeus.util.CsvReader;
import ch.ethz.idsc.amodeus.util.CsvReader.Row;
import ch.ethz.idsc.amodeus.util.Duration;
import ch.ethz.idsc.amodeus.util.math.SI;
import ch.ethz.idsc.tensor.Scalar;
import ch.ethz.idsc.tensor.Scalars;
import ch.ethz.idsc.tensor.Tensor;
import ch.ethz.idsc.tensor.qty.Quantity;

public abstract class TaxiTripsReader {
    private final String delim;
    private final Map<String, Integer> taxiIds = new HashMap<>();
    private final List<String> unreadable = new ArrayList<>();

    public TaxiTripsReader(String delim) {
        this.delim = delim;
    }

    public Stream<TaxiTrip> getTripStream(File file) throws IOException {
        final AtomicInteger tripIds = new AtomicInteger(0);
        List<TaxiTrip> list = new LinkedList<>();
        CsvReader reader = new CsvReader(file, delim);
        // TODO currently the headers in the "unreadable file" are in the wrong order
        unreadable.add(reader.headers().stream().collect(Collectors.joining(",")));
        reader.rows(row -> {
            int tripId = tripIds.getAndIncrement();
            if (tripId % 1000 == 0)
                System.out.println("trips: " + tripId);
            try {
                String taxiCode = getTaxiCode(row);
                int taxiId = taxiIds.getOrDefault(taxiCode, taxiIds.size());
                taxiIds.put(taxiCode, taxiId);
                LocalDateTime pickupTime = getStartTime(row);
                LocalDateTime dropoffTime = getEndTime(row);
                Scalar durationCompute = Duration.between(pickupTime, dropoffTime);
                Scalar durationDataset = getDuration(row);
                if (Scalars.lessEquals(Quantity.of(0.1, SI.SECOND), //
                        durationDataset.subtract(durationCompute).abs()))
                    System.err.println("Mismatch between duration recorded in data and computed duration," + //
                    "computed duration using start and end time: " + //
                    pickupTime + " --> " + dropoffTime + " != " + durationDataset);
                TaxiTrip trip = TaxiTrip.of(//
                        tripId, // TODO can I do real trip ID from CSV?
                        Integer.toString(taxiId), //
                        getPickupLocation(row), //
                        getDropoffLocation(row), //
                        getDistance(row), //
                        getWaitingTime(row), //
                        pickupTime, //
                        dropoffTime);
                list.add(trip);
            } catch (Exception exception) {
                System.err.println("Unable to read row: " + row);
                unreadable.add(row.toString());
            }
        });
        return list.stream();
    }

    public int getNumberOfTaxis() {
        return taxiIds.size();
    }

    public void saveUnreadable(File file) {
        System.err.println("Saving unreadable lines to: " + file.getAbsolutePath());
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            unreadable.stream().forEach(s -> {
                try {
                    bufferedWriter.write(s + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public abstract String getTaxiCode(Row row);

    public abstract LocalDateTime getStartTime(Row row) throws ParseException;

    public abstract LocalDateTime getEndTime(Row row) throws ParseException;

    public abstract Tensor getPickupLocation(Row row);

    public abstract Tensor getDropoffLocation(Row row);

    public abstract Scalar getDuration(Row row);

    public abstract Scalar getDistance(Row row);

    public abstract Scalar getWaitingTime(Row row);
}