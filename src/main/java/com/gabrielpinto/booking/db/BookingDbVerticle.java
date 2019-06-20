package com.gabrielpinto.booking.db;

import com.gabrielpinto.booking.utils.Loggable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BookingDbVerticle extends AbstractVerticle implements Loggable {
  private static final String BOOK_PHONE = "bookPhone";
  private static final String RETURN_PHONE = "returnPhone";
  private static final String GET_PHONES = "getPhones";

  private final Map<String, JsonObject> phones;
  private final ReadWriteLock readWriteLock;

  public BookingDbVerticle(JsonObject config) {
    this.phones = new ConcurrentHashMap<>();
    this.readWriteLock = new ReentrantReadWriteLock();
    config.getJsonArray("devices").stream()
      .map(String.class::cast)
      .forEach(device -> phones.put(device, new JsonObject()
        .put("booked", false).putNull("user").putNull("bookingDate")
        .put("device", device)));
  }

  @Override
  public void start(Future<Void> startFuture) {
    registerConsumers();
  }

  private void registerConsumers() {
    vertx.eventBus().consumer(BOOK_PHONE, this::bookPhone);
    vertx.eventBus().consumer(RETURN_PHONE, this::returnPhone);
    vertx.eventBus().consumer(GET_PHONES, this::getPhones);
  }

  private void bookPhone(Message<JsonObject> message) {
    final JsonObject body = message.body();
    final String device = body.getString("device");
    final String user = body.getString("user");
    readWriteLock.writeLock().lock();
    final JsonObject phone = phones.get(device);
    if (phone.getBoolean("booked")) {
      message.reply(new JsonObject().put("success", false).put("reason", "booked"));
    } else {
      final JsonObject updatedPhone = phone
        .put("booked", true)
        .put("user", user)
        .put("bookingDate", ZonedDateTime.now().toString());
      phones.put(device, updatedPhone);
      message.reply(updatedPhone);
    }
    readWriteLock.writeLock().unlock();
  }

  private void returnPhone(Message<JsonObject> message) {
    final JsonObject body = message.body();
    final String device = body.getString("device");
    final String user = body.getString("user");
    readWriteLock.writeLock().lock();
    final JsonObject phone = phones.get(device);
    if (!phone.getBoolean("booked")) {
      message.reply(new JsonObject()
        .put("success", false)
        .put("reason", "not booked"));
    } else if (!phone.getString("user").equals(user)){
      message.reply(new JsonObject()
        .put("success", false)
        .put("reason", "user"));
    } else {
      final JsonObject updatedPhone = phone
        .put("booked", false)
        .putNull("user")
        .put("bookingDate", ZonedDateTime.now().toString());
      phones.put(device, updatedPhone);
      message.reply(updatedPhone);
    }
    readWriteLock.writeLock().unlock();
  }

  private void getPhones(Message<JsonArray> message) {
    readWriteLock.readLock().lock();
    final JsonArray values = phones.values().stream()
      .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);
    message.reply(values);
    readWriteLock.readLock().unlock();
  }

  public static class Client implements BookingApi {
    private final EventBus eventBus;

    public Client(EventBus client) {
      this.eventBus = client;
    }

    @Override
    public Single<JsonObject> bookPhone(String device, String user) {
      final JsonObject message = new JsonObject()
        .put("device", device)
        .put("user", user);
      return eventBus.<JsonObject>rxSend(BookingDbVerticle.BOOK_PHONE, message)
        .map(Message::body);
    }

    @Override
    public Single<JsonObject> returnPhone(String device, String user) {
      final JsonObject message = new JsonObject()
        .put("device", device)
        .put("user", user);
      return eventBus.<JsonObject>rxSend(BookingDbVerticle.RETURN_PHONE, message)
        .map(Message::body);
    }

    @Override
    public Single<JsonArray> getPhones() {
      return eventBus.<JsonArray>rxSend(BookingDbVerticle.GET_PHONES, null)
        .map(Message::body);
    }
  }
}
