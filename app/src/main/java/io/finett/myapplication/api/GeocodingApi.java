package io.finett.myapplication.api;

import io.finett.myapplication.model.GeocodingResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeocodingApi {

    @GET("v1/search")
    Call<GeocodingResponse> searchLocation(
            @Query("name") String cityName,
            @Query("count") int count,
            @Query("language") String language,
            @Query("format") String format
    );
}