package com.gabrielpinto.booking.db;

import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface BookingApi {
  Single<JsonObject> bookPhone(String device, String user);
  Single<JsonObject> returnPhone(String device, String user);
  Single<JsonArray> getPhones();
}
