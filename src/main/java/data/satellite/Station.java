package data.satellite;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Station {
    private final String id;

    private final int nbChannels;

    public Station(
            @JsonProperty("id") String id,
            @JsonProperty("nbChannels") int nbChannels
    ) {
        this.id = id;
        this.nbChannels = nbChannels;
    }

    public String getId() {
        return this.id;
    }

    public int getNbChannels() {
        return this.nbChannels;
    }

    @Override
    public String toString() {
        return "Station[" +
                "id: " + this.id + ", " +
                "nbChannels: " + this.nbChannels +
                "]";
    }
}
