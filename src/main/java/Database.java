import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

class Database {
//	private static Database ourInstance = new Database();
//
//	public static Database getInstance() {
//		return ourInstance;
//	}

	/**
	 * The default port.
	 */
	int DEFAULT_PORT = 5432;

	/**
	 * The default database name.
	 */
	String DEFAULT_DATABASE = "testdb";

	/**
	 * The default database user.
	 */
	String DEFAULT_USER = "vertx";

	/**
	 * The default user password.
	 */
	String DEFAULT_PASSWORD = "password";

	/**
	 * The default charset.
	 */
	String DEFAULT_CHARSET = "UTF-8";

	/**
	 * The default timeout for connect.
	 */
	long DEFAULT_CONNECT_TIMEOUT = 10000L;

	/**
	 * The default timeout for tests.
	 */
	long DEFAULT_TEST_TIMEOUT = 10000L;

	private SQLClient connection;
	private JsonObject configs;

	Database(Vertx vertx, String host, int port, String user, String pass) {
		this.setConfig(host, port, user, pass);
		this.connection = PostgreSQLClient.createShared(vertx, this.configs);
		this.connection.getConnection(res -> {
			if (res.succeeded()) {
				System.out.println("Success!");
			} else {
				System.out.println("Failed!");
			}
		});
	}

	private void setConfig(String host, int port, String user, String pass) {
		this.configs = new JsonObject();
		this.configs.put("host", host);
		this.configs.put("port", port);
		this.configs.put("username", user);
		this.configs.put("password", pass);
		this.configs.put("database", user);
	}

	Boolean insertRule(Rule rule) {
		String query = "INSERT INTO public.rules (id, rule_id, clause, relatives) VALUES (DEFAULT, ?, ?, ?)";
		JsonArray params = new JsonArray();
		params.add(rule.getId());
		params.add(rule.getClause());
		params.add(rule.getRelatives());

//		CompletableFuture<Boolean> result = new CompletableFuture<>();
//
//		connection.queryWithParams(query, params, res -> {
//			if (res.succeeded()) {
//				// Get the result set
//				ResultSet resultSet = res.result();
//				System.out.println("Success!");
//				result.complete(true);
//			} else {
//				// Failed!
//				System.out.println("Failed!");
//				result.complete(false);
//			}
//
//			result.isDone();
//		});
//
//		return result.get();

		connection.queryWithParams(query, params, res -> {
			if (res.succeeded()) {
				System.out.println("Success!");
			} else {
				// Failed!
				System.out.println("Failed!");
			}
		});

		return true;
	}

}
