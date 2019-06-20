package com.gabrielpinto.booking.http;

import com.gabrielpinto.booking.delegate.PhoneDelegate;
import com.gabrielpinto.booking.utils.Loggable;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.templ.handlebars.HandlebarsTemplateEngine;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

public class HttpServerVerticle extends AbstractVerticle implements Loggable {
  private static final int KB = 1024;

  private final PhoneDelegate phoneDelegate;
  private final Router router;
  private final HandlebarsTemplateEngine engine;
  private final AuthProvider authProvider;
  private final JsonObject config;

  public HttpServerVerticle(final PhoneDelegate phoneDelegate,
                            final Router router,
                            final HandlebarsTemplateEngine engine,
                            final AuthProvider authProvider,
                            final JsonObject config) {
    this.phoneDelegate = phoneDelegate;
    this.router = router;
    this.engine = engine;
    this.authProvider = authProvider;
    this.config = config;
  }

  @Override
  public void start(Future<Void> startFuture) {
    router.route().handler(BodyHandler.create().setBodyLimit(KB));
    router.route().handler(CookieHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setAuthProvider(authProvider));

    router.route("/login").method(GET).handler(this::login);
    router.route("/").handler(RedirectAuthHandler.create(authProvider, "/booking/devices"));
    router.route("/booking/*").handler(RedirectAuthHandler.create(authProvider, "/login"));
    router.route("/booking/devices").method(GET).handler(this::getLatest);
    router.route("/booking/book/:device").method(POST).handler(this::bookPhone);
    router.route("/booking/return/:device").method(POST).handler(this::returnPhone);
    router.route("/login/auth").handler(FormLoginHandler.create(authProvider));
    router.route("/logout").handler(this::logout);

    vertx.createHttpServer().requestHandler(router)
      .rxListen(config.getInteger("port", 8080))
      .subscribe(http -> {
        startFuture.complete();
        log().info("HTTP server started on port 8080");
      }, error -> {
        log().error("Failed to start HTTP server on port 8080", error);
        startFuture.fail(error);
      });
  }

  private void login(RoutingContext ctx) {
    engine.rxRender(new JsonObject(), "templates/login.hbs")
      .subscribe(res -> ctx.response().setStatusCode(OK.code()).end(res),
        e -> {
          log().error("Failed to render login page.", e);
          ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
        });
  }

  private void logout(RoutingContext ctx) {
    ctx.clearUser();
    ctx.response().putHeader("location", "/").setStatusCode(302).end();
  }

  private void returnPhone(RoutingContext ctx) {
    final String user = ctx.user().principal().getString("username");
    final String device = ctx.pathParam("device");
    phoneDelegate.returnPhone(device, user)
      .subscribe(res -> {
        if (res.getBoolean("success", true)) {
          ctx.reroute(GET, "/booking/devices");
        } else {
          final String reason = res.getString("reason");
          if (reason.equals("not booked")) {
            ctx.response().setStatusCode(HttpResponseStatus.CONFLICT.code()).end("Device is not booked.");
          } else if (reason.equals("user")) {
            ctx.response().setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end("Device is already booked by another user.");
          } else {
            ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
          }
        }
      }, e -> {});
  }

  private void bookPhone(RoutingContext ctx) {
    final String user = ctx.user().principal().getString("username");
    final String device = ctx.pathParam("device");
    phoneDelegate.bookPhone(device, user)
      .subscribe(res -> {
        if (res.getBoolean("success", true)) {
          ctx.reroute(GET, "/booking/devices");
        } else {
          final String reason = res.getString("reason");
          if (reason.equals("booked")) {
            ctx.response().setStatusCode(HttpResponseStatus.CONFLICT.code()).end("Device is already booked by another user.");
          } else {
            ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
          }
        }
      });
  }

  private void getLatest(RoutingContext ctx) {
    phoneDelegate.getLatest()
      .flatMap(data -> engine.rxRender(data, "templates/devices.hbs"))
      .subscribe(res -> ctx.response().setStatusCode(OK.code()).end(res),
        e -> {
          log().error("Failed to get latest phones.", e);
          ctx.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
        });
  }
}
