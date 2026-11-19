
package com.example.abdullah_ahmed_s2530964;

public class TrafficEvent {

    private final String id;
    private final String type;
    private final String description;
    private final String startTime;
    private final String endTime;
    private final String location;
    private final String category;

    // Coordinates used for distance searching
    private final double latitude;
    private final double longitude;

    public TrafficEvent(String id, String type, String description,
                        String startTime, String endTime,
                        String location, String category,
                        double latitude, double longitude) {

        this.id = id;
        this.type = type;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Existing constructor kept for compatibility
    public TrafficEvent(String id, String type, String description,
                        String startTime, String endTime,
                        String location, String category) {

        this(id, type, description, startTime, endTime,
                location, category, Double.NaN, Double.NaN);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getLocation() {
        return location;
    }

    public String getCategory() {
        return category;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean hasCoordinates() {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    @Override
    public String toString() {
        return type + "\n"
                + description + "\n"
                + "Location: " + location;
    }
}
