import com.sun.deploy.net.HttpRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import sun.net.www.http.HttpClient;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;

public class Client extends AbstractVerticle {

	public static void main(String[] args) {


//		HttpRequest request = HttpRequest.newBuilder()
//				.uri(URI.create("http://openjdk.java.net/"))
//				.timeout(Duration.ofMinutes(1))
//				.header("Content-Type", "application/json")
//				.POST(BodyPublishers.ofFile(Paths.get("file.json")))
//				.build();
	}

	@Override
	public void start() throws Exception {
		HttpClientRequest request = vertx.createHttpClient().get(8443, "localhost", "/");
		request.handler(resp -> {

			// Print custom frames received from server
			resp.customFrameHandler(frame -> {
				System.out.println("Got frame from server " + frame.payload().toString("UTF-8"));
			});
		});

		request.sendHead(version -> {

			// Once head has been sent we can send custom frames

			vertx.setPeriodic(1000, timerID -> {

				System.out.println("Sending ping frame to server");
				request.writeCustomFrame(10, 0, Buffer.buffer("ping"));
			});
		});
//		fs.open("content.txt", new OpenOptions(), fileRes -> {
//			if (fileRes.succeeded()) {
//				ReadStream<Buffer> fileStream = fileRes.result();
//
//				String fileLen = "1024";
//
//				// Send the file to the server using POST
//				client
//						.post(8080, "myserver.mycompany.com", "/some-uri")
//						.putHeader("content-length", fileLen)
//						.sendStream(fileStream, ar -> {
//							if (ar.succeeded()) {
//								// Ok
//							}
//						});
//			}
//		});
	}
}
