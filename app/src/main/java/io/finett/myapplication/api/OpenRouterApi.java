package io.finett.myapplication.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import java.util.Map;

public interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    Call<Map<String, Object>> getChatCompletion(
        @Header("Authorization") String authorization,
        @Header("HTTP-Referer") String referer,
        @Header("X-Title") String title,
        @Body Map<String, Object> body
    );
} 