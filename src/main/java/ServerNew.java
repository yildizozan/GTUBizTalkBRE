import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;

public class ServerNew extends AbstractVerticle {

	@Override
	public void start() {
		HttpServer server = vertx.createHttpServer();

		server.requestHandler(request -> {

			// This handler gets called for each request that arrives on the server
			HttpServerResponse response = request.response();
			response.putHeader("content-type", "application/xml");

			// Write to the response and end it
			response.end("Hello World!");
		});

		server.listen(8080);
	}
}