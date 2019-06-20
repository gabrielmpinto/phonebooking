package com.gabrielpinto.booking;

import com.gabrielpinto.booking.db.BookingDbVerticle;
import com.gabrielpinto.booking.delegate.PhoneDelegate;
import com.gabrielpinto.booking.http.HttpServerVerticle;
import com.gabrielpinto.booking.services.FonoApiVerticle;
import com.gabrielpinto.booking.utils.Loggable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.shiro.ShiroAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.auth.shiro.ShiroAuth;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.templ.handlebars.HandlebarsTemplateEngine;

public class MainVerticle extends AbstractVerticle implements Loggable {

  @Override
  public void start(Future<Void> startFuture) {
    final var configRetriever = buildConfigRetriever();
    configRetriever.rxGetConfig()
      .subscribe(config -> {
        final var router = Router.router(vertx);
        final var engine = HandlebarsTemplateEngine.create(vertx);
        final var fonoApi = new FonoApiVerticle.Client(vertx.eventBus());
        final var bookingApi = new BookingDbVerticle.Client(vertx.eventBus());
        final var phoneDelegate = new PhoneDelegate(fonoApi, bookingApi);
        final var authProvider = ShiroAuth.create(vertx, buildShiroAuthOptions());

        final var fonoApiVerticle = new FonoApiVerticle();
        final var bookingDbVerticle = new BookingDbVerticle(config);
        final var httpServerVerticle = new HttpServerVerticle(phoneDelegate, router, engine, authProvider, config);

        Flowable.<AbstractVerticle>fromArray(fonoApiVerticle, bookingDbVerticle, httpServerVerticle)
          .flatMapSingle(this::deployVerticle)
          .toList()
          .subscribe(v -> {
            log().info("Successfully deployed all verticles.");
            startFuture.complete();
          }, e -> {
            log().error("Failed to start application.", e);
            startFuture.fail(e);
          });
      }, e -> {
        log().error("Failed to retrieve configuration.", e);
        startFuture.fail(e);
      });
  }

  private ConfigRetriever buildConfigRetriever() {
    var fileStore = new ConfigStoreOptions()
      .setType("file")
      .setConfig(new JsonObject().put("path", "config.json"));
    var options = new ConfigRetrieverOptions()
      .addStore(fileStore);
    return ConfigRetriever.create(vertx, options);
  }

  private ShiroAuthOptions buildShiroAuthOptions() {
    return new ShiroAuthOptions()
      .setType(ShiroAuthRealmType.PROPERTIES)
      .setConfig(new JsonObject());
  }

  private Single<String> deployVerticle(AbstractVerticle verticle) {
    return vertx.rxDeployVerticle(verticle)
      .doOnError(e -> log().error("Failed to deploy verticle {}", e, verticle));
  }
}
