package com.tikal.angelsense.gpsservice;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

public class GpsFinderServiceVerticle extends AbstractVerticle {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GpsFinderServiceVerticle.class);
	private RedisClient redis;

	@Override
	public void start() {
		redis = RedisClient.create(vertx, new RedisOptions().setHost(config().getString("redis-host")));

		final Router router = Router.router(vertx);
		router.route(HttpMethod.GET, "/gps/angel/:angelId").handler(this::handleQuery);
		// Allow outbound traffic to the gps-feed address
		final BridgeOptions options = new BridgeOptions().addOutboundPermitted(new PermittedOptions().setAddress("gps-feed"));
		router.route("/eventbus/*").handler(SockJSHandler.create(vertx).bridge(options, this::handleBridgeEvent));
		router.route().handler(StaticHandler.create());
		vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http-port"));

		logger.info("Started the HTTP server...");

	}

	private void handleBridgeEvent(final BridgeEvent event) {
		if (event.type() == BridgeEventType.SOCKET_CREATED)
			logger.info("A socket was created");
		event.complete(true);
	}

	private void handleQuery(final RoutingContext routingContext) {
		final String angelId = routingContext.request().getParam("angelId");
		if (angelId == null)
			routingContext.response().setStatusCode(400).setStatusMessage("angelId is missing").end();
		else {
			final String start = routingContext.request().params().get("start");
			final String stop = routingContext.request().params().get("stop");
			redis.zrange("gps.angel." + angelId, Long.valueOf(start), Long.valueOf(stop),ar -> handleRedisQuery(ar, routingContext));
		}
	}

	private void handleRedisQuery(final AsyncResult<JsonArray> ar, final RoutingContext routingContext) {
		if (ar.succeeded()) {
			routingContext.response().end(Buffer.factory.buffer(ar.result().toString()));
		} else {
			logger.error("Failed to run Redis query: ", ar.cause());
			routingContext.response().setStatusCode(500).setStatusMessage(ar.cause().getMessage()).end();
		}
	}

}
