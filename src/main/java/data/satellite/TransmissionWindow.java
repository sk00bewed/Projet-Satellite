package data.satellite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class TransmissionWindow {

    private final String satelliteId;
    private final String stationId;

    private final LocalDateTime start;

    private final LocalDateTime end;

    @JsonCreator
    public TransmissionWindow(
            @JsonProperty("satelliteId") String satelliteId,
            @JsonProperty("stationId") String stationId,
            @JsonProperty("start") LocalDateTime start,
            @JsonProperty("end") LocalDateTime end
    ) {
        this.satelliteId = satelliteId;
        this.stationId = stationId;
        this.start = start;
        this.end = end;
    }

    public String getSatelliteId() {
        return this.satelliteId;
    }

    public String getStationId() {
        return this.stationId;
    }

    public LocalDateTime getStart() {
        return this.start;
    }

    public LocalDateTime getEnd() {
        return this.end;
    }

    @Override
    public String toString() {
        return "Window[" +
                "satelliteId: " + this.satelliteId + ", " +
                "stationId: " + this.stationId + ", " +
                "start: " + this.start + ", " +
                "end: " + this.end +
                "]";
    }
}
