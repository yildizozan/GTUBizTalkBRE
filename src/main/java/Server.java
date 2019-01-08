import SatSolver.Business;
import SatSolver.Response;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

public class Server extends AbstractVerticle {

	private RedisClient redisClient;
	private Future<Void> future;

	public static void main(String[] args) {
		VertxOptions vxOptions = new VertxOptions()
				.setBlockedThreadCheckInterval(200000000);

		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(new Server());
	}

	@Override
	public void start(Future<Void> future) {

		this.future = future;

		Server that = this;

		// DB
		final JsonObject configs = new JsonObject();
		configs.put("host", "localhost");
		configs.put("port", 5432);
		configs.put("username", "postgres");
		configs.put("password", "mysecretpassword");
		configs.put("database", "postgres");
		final SQLClient sqlClient = PostgreSQLClient.createShared(vertx, configs);


		// Cacher server
		RedisOptions job = new RedisOptions().setHost("localhost").setPort(6379);
		redisClient = RedisClient.create(vertx, job);

		Router router = Router.router(vertx);

		// Middleware for db connection
		router.route().handler(routingContext -> sqlClient.getConnection(res -> {
			if (res.failed()) {
				routingContext.fail(res.cause());
			} else {
				SQLConnection conn = res.result();

				// save the connection on the context
				routingContext.put("conn", conn);

				// we need to return the connection back to the jdbc pool. In order to do that we need to close it, to keep
				// the remaining code readable one can add a headers end handler to close the connection.
				routingContext.addHeadersEndHandler(done -> conn.close(v -> {
				}));

				System.out.println("DB connected!");
				routingContext.next();
			}
		})).failureHandler(routingContext -> {
			System.out.println(routingContext.failed());
			SQLConnection conn = routingContext.get("conn");
			if (conn != null) {
				conn.close(v -> {
				});
			}
		});

		// Middleware for cache server
		router.route().handler(RoutingContext::next);

		// Middleware for request body parsing
		router.route().handler(BodyHandler.create());

		router.put("/rule").handler(that::handlePutRole);
		router.put("/rule/answer").handler(that::handlePutAnswer);

//		router.route().method(HttpMethod.PUT).path("/rule").handler(rc -> {
//
//			// This handler will be called for every request
//			HttpServerResponse response = rc.response();
//			response.putHeader("content-type", "application/xml");
//
//			// Gelen http isteğindeki xml ifadeyi string olarak alıyoruz
//			String body = rc.getBodyAsString();
//
//			// String olarak almış olduğumuz xml ifadeyi parse edilmesi için rule objemize veriyoruz.
//			Rule rule = new Rule(body);
//
//			if (body != null) {
//				String query = "INSERT INTO public.rules (id, rule_id, clause, relatives) VALUES (DEFAULT, ?, ?, ?)";
//				JsonArray params = new JsonArray();
//				params.add(rule.getId());
//				params.add(rule.getClause());
//				params.add(rule.getRelatives());
//
//				client.queryWithParams(query, params, res -> {
//					if (res.succeeded()) {
//						// Success!
//						rc.response().setStatusCode(201).end("<result>true</result>");
//					} else {
//						// Failed!
//						rc.response().setStatusCode(400).end("<result>false</result>");
//					}
//				});
//			} else {
//				rc.response().setStatusCode(400).end("<result>false</result>");
//			}
//
//
//		});
//
//		router.route().method(HttpMethod.POST).path("/answer").handler(routingContext -> {
//
//			// This handler will be called for every request
//			HttpServerResponse response = routingContext.response();
//			response.putHeader("content-type", "application/xml");
//
//			// Write to the response and end it
//			response.end("<job>true</job>");
//		});


		vertx.createHttpServer().requestHandler(router).listen(8080);
	}

	private void handlePutRole(RoutingContext rc) {

		SQLConnection conn = rc.get("conn");

		// This handler will be called for every request
		HttpServerResponse response = rc.response();
		response.putHeader("content-type", "application/xml");

		// Gelen http isteğindeki xml ifadeyi string olarak alıyoruz
		String body = rc.getBodyAsString();

		if (body == null) {
			rc.response().setStatusCode(400).end("<result id='23'>false</result>");
			return;
		}

		// Cache'den sistemin durum durmadığı kontrol edilir.

		// String olarak almış olduğumuz xml ifadeyi parse edilmesi için rule objemize veriyoruz.
		Rule rule = new Rule();
		try {
			rule.setFromXML(body);
			System.out.println(rule);
		} catch (ParserConfigurationException | IOException | SAXException e) {
			e.printStackTrace();
			rc.response().setStatusCode(400).end("<result id='24'>false</result>");
			return;
		}

		String queryCheck = "SELECT * FROM public.rules WHERE rule_id = ?";
		JsonArray queryCheckParams = new JsonArray();
		queryCheckParams.add(rule.getId());
		conn.queryWithParams(queryCheck, queryCheckParams, resCheck -> {
//			System.out.println(resCheck.result().getRows());
// qgXPHS6Zx1fojgDi javafx.util.Pair

			// Veri db'ye daa önce kaydedilmişse.
			if (resCheck.result().getRows().size() != 0) {
				rc.response().setStatusCode(200).end("<result>exist</result>");
			} else {
				String queryInsert = "INSERT INTO public.rules (rule_id, clause, relatives) VALUES (?, ?, ?)";
				JsonArray queryInsertParams = new JsonArray();
				queryInsertParams.add(rule.getId());
				queryInsertParams.add(rule.getClause());
				queryInsertParams.add(rule.getRelatives());

				conn.queryWithParams(queryInsert, queryInsertParams, resInsert -> {
					if (resInsert.succeeded()) {
						// Success!
						rc.response().setStatusCode(201).end("<result>true</result>");
					} else {
						// Failed!
						rc.response().setStatusCode(400).end("<result id='25'>false</result>");
					}
				});
			}
		});

	}

	private void handlePutAnswer(RoutingContext rc) {
		SQLConnection conn = rc.get("conn");

		// This handler will be called for every request
		HttpServerResponse response = rc.response();
		response.putHeader("content-type", "application/xml");

		// Gelen http isteğindeki xml ifadeyi string olarak alıyoruz
		String body = rc.getBodyAsString();

		if (body == null) {
			rc.response().setStatusCode(400).end("<result>false</result>");
			return;
		}

		// String olarak almış olduğumuz xml ifadeyi parse edilmesi için answer objemize veriyoruz.
		Response resp = new Response();
		try {
			resp.setFromXML(body);
		} catch (ParserConfigurationException | IOException | SAXException e) {
			e.printStackTrace();
			rc.response().setStatusCode(400).end("<result id='45'>false</result>"); // TODO: false yapılacak
			return;
		}

		System.out.println(resp); // Checkpoint

		JsonArray params = new JsonArray();
		params.add(resp.getRuleID());
		params.add(resp.getUserID());
		params.add(resp.getAnswer());

		JsonArray paramsForGet = new JsonArray();
		paramsForGet.add(resp.getRuleID());

		String query = "INSERT INTO public.responses (rule_id, user_id, answer) VALUES (?, ?, ?)";
		conn.queryWithParams(query, params, resInsert -> {
//			this.future.fail(resInsert.cause());
			if (resInsert.succeeded()) {

				// Tüm cevaplar db'den çekilecek.
				String queryGetAllResponses = "SELECT rule_id, user_id, answer FROM public.responses WHERE rule_id = ?";
				conn.queryWithParams(queryGetAllResponses, paramsForGet, resGetAnsAll -> {
//					this.future.fail(resGetAll.cause());
					if (resGetAnsAll.succeeded()) {

						// Rule db'den çekilecek.
						String queryGetRule = "SELECT rule_id, clause, relatives FROM public.rules WHERE rule_id = ? LIMIT 1";
						conn.queryWithParams(queryGetRule, paramsForGet, resGetRule -> {
							if (resGetRule.succeeded()) {

								JsonObject rule = resGetRule.result().getRows().get(0); // OK
								List<JsonObject> responses = resGetRule.result().getRows();

								// Calculation.
								Business business = new Business();
//								bre.rule = rule.getString("clause");

								for (JsonObject respons : responses) {
									System.out.println(respons);

									business.rule = rule.getString("clause");
									String userID = respons.getString("user_id");
									String answer = respons.getString("answer");
									String ruleID = respons.getString("rule_id");
									business.solver(userID, answer, ruleID);

									System.out.println(business.rule);
								}

								rc.response().setStatusCode(200).end("<result>true</result>");
							} else {
								// DB'den tüm veriler çekilemedi demektir.
								rc.response().setStatusCode(500).end("<result>false</result>");
							}
						});
					} else {
						// DB'den tüm veriler çekilemedi demektir.
						rc.response().setStatusCode(500).end("<result>false</result>");
					}
				});

			} else {
				// Failed!
				rc.response().setStatusCode(400).end("<result>false</result>");
			}
		});
	}

}
