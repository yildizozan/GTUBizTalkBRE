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
import java.util.HashMap;
import java.util.List;

/*
create table responses
(
	id uuid default uuid_generate_v4() not null
		constraint responses_pk
			primary key,
	rule_id text not null,
	user_id text not null,
	answer varchar(1) default 'f'::character varying not null
);

alter table responses owner to postgres;


create table rules
(
	id uuid default uuid_generate_v4() not null
		constraint rules_pk
			primary key,
	rule_id text not null,
	clause text not null,
	relatives text not null,
	created_at timestamp default now() not null
);

alter table rules owner to postgres;


 */
public class Server extends AbstractVerticle {

	HashMap<String , String> rules = new HashMap<String, String>();

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
			System.out.println("failureHandler err " + routingContext.failed());
			SQLConnection conn = routingContext.get("conn");
			if (conn != null) {
				conn.close(v -> {
					routingContext.response().setStatusCode(500).end("<result>err conn</result>");
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

		Business business = new Business();
		final String result = business.firstCheck(rule.getClause(), rule.getRelatives());
		System.out.println(rule.getRelatives());
		System.out.println(result);
		if (!result.equals("true")) {
			rc.response().setStatusCode(400).end("<result id='24'>" + result + "</result>");
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

		JsonArray paramsForGetAnswers = new JsonArray();
		paramsForGetAnswers.add(resp.getRuleID());

		String query = "INSERT INTO public.responses (rule_id, user_id, answer) VALUES (?, ?, ?)";
		conn.queryWithParams(query, params, resInsert -> {
			if (resInsert.succeeded()) {

				// Tüm cevaplar db'den çekilecek.
				String queryGetAllResponses = "SELECT rule_id, user_id, answer FROM public.responses WHERE rule_id = ?";
				conn.queryWithParams(queryGetAllResponses, paramsForGetAnswers, resGetAnsAll -> {
					System.out.println("Rule: " + resGetAnsAll.result().getRows().get(0).toString());
					if (resGetAnsAll.succeeded()) {

						// Rule db'den çekilecek.
						String queryGetRule = "SELECT rule_id, clause, relatives FROM public.rules WHERE rule_id = ? LIMIT 1";
						conn.queryWithParams(queryGetRule, paramsForGetAnswers, resGetRule -> {
							if (resGetRule.succeeded()) {

								JsonObject rule = resGetRule.result().getRows().get(0); // OK
								List<JsonObject> responses = resGetAnsAll.result().getRows();
								System.out.println(responses); // Checkpoint responses

								// Calculation.
								Business business = new Business();

								String result = "f";
								for (JsonObject rsp : responses) {
									System.out.println(rsp); // Checkpoint response
									business.rule = rule.getString("clause");

									String userID = rsp.getString("user_id");
									String answer = rsp.getString("answer");
									String ruleID = rsp.getString("rule_id");

									result = business.solver(userID, answer);
									rules.put(ruleID, business.rule);

									System.out.println(result);
									System.out.println(business.rule);
								}

								rc.response().setStatusCode(200).end("<result>" + result + "</result>");
								return;
							} else {
								// DB'den tüm veriler çekilemedi demektir.
								rc.response().setStatusCode(500).end("<result>false</result>");
								return;
							}
						});
					} else {
						// DB'den tüm veriler çekilemedi demektir.
						rc.response().setStatusCode(500).end("<result>false</result>");
						return;
					}
				});

			} else {
				// Failed!
				rc.response().setStatusCode(400).end("<result>false</result>");
				return;
			}
		});
	}

}
