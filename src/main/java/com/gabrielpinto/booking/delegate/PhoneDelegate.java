package com.gabrielpinto.booking.delegate;

import com.gabrielpinto.booking.db.BookingApi;
import com.gabrielpinto.booking.services.FonoApi;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Comparator;

import static io.reactivex.internal.functions.Functions.identity;

public class PhoneDelegate {
  private FonoApi fonoApi;
  private BookingApi bookingApi;

  public PhoneDelegate(FonoApi fonoApi, BookingApi bookingApi) {
    this.fonoApi = fonoApi;
    this.bookingApi = bookingApi;
  }

  public Single<JsonObject> bookPhone(String device, String user) {
    return bookingApi.bookPhone(device, user);
  }

  public Single<JsonObject> returnPhone(String device, String user) {
    return bookingApi.returnPhone(device, user);
  }

  public Single<JsonObject> getLatest() {
    return bookingApi.getPhones()
      .flattenAsFlowable(identity())
      .cast(JsonObject.class)
      .flatMapSingle(this::getDevice)
      .sorted(Comparator.comparing(device -> device.getString("device")))
      .collectInto(new JsonArray(), JsonArray::add)
      .map(phones -> new JsonObject().put("phones", phones));
  }

  private Single<JsonObject> getDevice(JsonObject device) {
    final String deviceName = device.getString("device");
    return fonoApi.getDevice(deviceName)
      .flattenAsFlowable(identity())
      .cast(JsonObject.class)
      .filter(apiDevice -> apiDevice.getString("DeviceName", "").equals(deviceName))
      .first(device)
      .map(apiDevice -> apiDevice.mergeIn(device));
  }
}
