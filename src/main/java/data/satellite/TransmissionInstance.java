package data.satellite;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public class TransmissionInstance {

    private final String id;
    private final Station[] stations;

    private final Satellite[] satellites;

    public TransmissionInstance(
            @JsonProperty("id") String id,
            @JsonProperty("stations") Station[] stations,
            @JsonProperty("satellites") Satellite[] satellites
    ) {
        this.id = id;
        this.stations = stations;
        this.satellites = satellites;
    }

    public String getId() {
        return this.id;
    }

    public Station[] getStations() {
        return this.stations;
    }

    public Satellite[] getSatellites() {
        return this.satellites;
    }

    @Override
    public String toString() {
        return "TransmissionInstance[" +
                "id: " + this.id + ", " +
                "stations: " + Arrays.toString(this.stations) + ", " +
                "satellites: " + Arrays.toString(this.satellites) +
                "]";
    }
}
