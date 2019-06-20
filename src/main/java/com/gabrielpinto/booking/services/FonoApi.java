package com.gabrielpinto.booking.services;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;

public interface FonoApi {
  Single<JsonArray> getLatest();
  Single<JsonArray> getDevice(String device);
}
