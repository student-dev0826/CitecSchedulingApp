package com.example.citecschedulingapp.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL =
            com.example.citecschedulingapp.BuildConfig.API_BASE_URL;

    private static Retrofit retrofit;
    private static ApiService api;

    public static synchronized Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            // Free hosting can be slow to wake up, so allow more than the 10s default.
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(25, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .writeTimeout(25, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }

    public static synchronized ApiService getApiService() {
        if (api == null) api = getRetrofitInstance().create(ApiService.class);
        return api;
    }
}
