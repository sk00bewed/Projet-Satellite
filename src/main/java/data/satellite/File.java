package data.satellite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Arrays;

public class File {
    private final String id;

    private final int size;

    private LocalDateTime releaseDateTime;

    private String[] predecessors;

    @JsonCreator
    public File(
            @JsonProperty("id") String id,
            @JsonProperty("size") int size,
            @JsonProperty("releaseDateTime") LocalDateTime releaseDateTime,
            @JsonProperty("predecessors") String[] predecessors
    ) {
        this.id = id;
        this.size = size;
        this.releaseDateTime = releaseDateTime;
        this.predecessors = predecessors;
    }

    public File(String id, int size) {
        this.id = id;
        this.size = size;
        this.predecessors = new String[0];
    }

    public String getId() {
        return this.id;
    }

    public int getSize() {
        return this.size;
    }

    public LocalDateTime getReleaseDateTime() {
        return this.releaseDateTime;
    }

    public String[] getPredecessors() {
        return this.predecessors;
    }

    public void setPredecessors(String[] predecessors) {
        this.predecessors = predecessors;
    }

    public void setReleaseDateTime(LocalDateTime releaseDateTime) {
        this.releaseDateTime = releaseDateTime;
    }

    @Override
    public String toString() {
        return "File[" +
                "id: " + this.id + ", " +
                "size: " + this.size + ", " +
                "releaseDateTime: " + this.releaseDateTime + ", " +
                "predecessors: " + Arrays.toString(predecessors) +
                "]";
    }
}
