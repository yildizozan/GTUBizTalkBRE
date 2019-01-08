import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServerTest extends AbstractVerticle {

	@Test
	public void start(TestContext context) {
		Async async = context.async();
		vertx.createHttpClient().post(8080, "localhost", "/")
				.putHeader("content-type", "application/xml")
//				.putHeader("content-length", length)
				.handler(response -> {
					context.assertEquals(response.statusCode(), 200);
					context.assertTrue(response.headers().get("content-type").contains("application/xml"));
					response.bodyHandler(body -> {
						final Whisky whisky = Json.decodeValue(body.toString(), Whisky.class);
//						context.assertEquals(whisky.getName(), "Jameson");
						context.assertEquals(whisky.getOrigin(), "Ireland");
//						context.assertNotNull(whisky.getId());
						async.complete();
					});
				})
				.write("<job><rule>asodkas</rule></job>")
				.end();
	}
}
