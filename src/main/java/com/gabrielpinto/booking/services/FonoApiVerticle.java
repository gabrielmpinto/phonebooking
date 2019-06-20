package com.gabrielpinto.booking.services;

import com.gabrielpinto.booking.utils.Loggable;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.util.UUID;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class FonoApiVerticle extends AbstractVerticle implements Loggable {
  private static final String GET_LATEST = "getLatest";
  private static final String GET_LASTEST_PATH = "/v1/getlatest";
  private static final String GET_DEVICE = "getDevice";
  private static final String GET_DEVICE_PATH = "/v1/getdevice";

  private static final String host = "fonoapi.freshpixl.com";
  private static final int port = 443;

  private WebClient client;
  private String token;

  @Override
  public void start(Future<Void> startFuture) {
    WebClientOptions options = buildWebClientOptions();

    client = WebClient.create(vertx, options);
    token = UUID.randomUUID().toString();

    client.post("/token/newToken")
      .rxSendForm(buildTokenForm())
      .map(HttpResponse::bodyAsJsonObject)
      .subscribe(response -> {
        if (response.getString("status", "").equals("success")) {
          log().debug("Successfully acquired new token for FonoApi.");
          registerConsumers();
          startFuture.complete();
        } else {
          startFuture.fail("Failed to acquire new token.");
        }
      }, e -> {
        log().error("Failed to acquire new token.");
        startFuture.fail(e);
      });
  }

  private MultiMap buildTokenForm() {
    return MultiMap.caseInsensitiveMultiMap().add("ApiToken", token);
  }

  private WebClientOptions buildWebClientOptions() {
    return new WebClientOptions()
      .setDefaultHost(host)
      .setDefaultPort(port)
      .setSsl(true)
      .setTrustAll(false)
      .setVerifyHost(true);
  }

  private void registerConsumers() {
    vertx.eventBus().consumer(GET_LATEST, this::getLatest);
    vertx.eventBus().consumer(GET_DEVICE, this::getDevice);
  }

  private void getLatest(Message<String> message) {
    client.get(GET_LASTEST_PATH)
      .addQueryParam("token", token)
      .rxSend()
      .map(response -> handleBody(response, HttpResponse::bodyAsJsonArray))
      .subscribe(message::reply, error -> log().error("{} - {} failed", error, HttpMethod.GET, GET_LASTEST_PATH));
  }

  private void getDevice(Message<String> message) {
    client.get(GET_DEVICE_PATH)
      .addQueryParam("token", token)
      .addQueryParam("device", message.body())
      .rxSend()
      .map(response -> handleBody(response, HttpResponse::bodyAsJsonArray))
      .subscribe(message::reply, error -> log().error("{} - {} failed", error, HttpMethod.GET, GET_DEVICE_PATH));
  }

  private <T> T handleBody(HttpResponse<Buffer> response, Function<HttpResponse<Buffer>, T> mapper) {
    if (response.statusCode() != HttpResponseStatus.OK.code()) {
      throw new HttpStatusException(response.statusCode());
    }

    return mapper.apply(response);
  }

  public static class Client implements FonoApi, Loggable {
    private final EventBus eventBus;

    public Client(EventBus client) {
      this.eventBus = client;
    }

    public Single<JsonArray> getLatest() {
      return eventBus.<JsonArray>rxSend(FonoApiVerticle.GET_LATEST, null)
        .map(Message::body);
    }

    @Override
    public Single<JsonArray> getDevice(String device) {
      requireNonNull(device);
      return eventBus.<JsonArray>rxSend(FonoApiVerticle.GET_DEVICE, device)
        .map(Message::body);
    }
  }
}
