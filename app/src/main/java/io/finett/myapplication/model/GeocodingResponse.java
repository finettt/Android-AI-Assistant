package io.finett.myapplication.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GeocodingResponse {
    @SerializedName("results")
    private List<GeocodingResult> results;

    @SerializedName("generationtime_ms")
    private double generationTimeMs;

    public List<GeocodingResult> getResults() {
        return results;
    }

    public double getGenerationTimeMs() {
        return generationTimeMs;
    }

    public static class GeocodingResult {
        @SerializedName("id")
        private long id;

        @SerializedName("name")
        private String name;

        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;

        @SerializedName("country")
        private String country;

        @SerializedName("country_code")
        private String countryCode;

        @SerializedName("timezone")
        private String timezone;

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public String getCountry() {
            return country;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public String getTimezone() {
            return timezone;
        }
    }
}