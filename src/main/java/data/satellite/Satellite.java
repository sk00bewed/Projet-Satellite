package data.satellite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Arrays;

public class Satellite {
    private final String id;

    private final int nbTransmissionChannels;
    @JsonIgnore
    private final File[] files;

    private final TransmissionWindow[] transmissionWindows;

    @JsonCreator
    public Satellite(
            @JsonProperty("id") String id,
            @JsonProperty("nbTransmissionChannels") int nbTransmissionChannels,
            @JsonProperty("files") File[] files,
            @JsonProperty("transmissionWindows") TransmissionWindow[] transmissionWindows
    ) {
        this.id = id;
        this.nbTransmissionChannels = nbTransmissionChannels;
        this.files = files;
        this.transmissionWindows = transmissionWindows;
    }

    public String getId() {
        return this.id;
    }

    public int getNbTransmissionChannels() {
        return this.nbTransmissionChannels;
    }

    public File[] getFiles() {
        return this.files;
    }

    public File getFile(int i) {
        return this.files[i];
    }

    public TransmissionWindow[] getTransmissionWindows() {
        return this.transmissionWindows;
    }

    public LocalDateTime[] getTransmissionWindow(String stationId) {
        for (TransmissionWindow transmissionWindow : this.transmissionWindows){
            if (transmissionWindow.getStationId().equals(stationId)) {
                return new LocalDateTime[]{transmissionWindow.getStart(), transmissionWindow.getEnd()};
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Satellite[" +
                "id: " + this.id + ", " +
                "nbTransmissionChannels: " + this.nbTransmissionChannels + ", " +
                "files: " + Arrays.stream(files).map(File::getId).toList() + ", " +
                "transmissionWindows: " + Arrays.toString(transmissionWindows) +
                "]";
    }
}
