package com.netflix.hystrix.contrib.rxnetty.metricsstream;

import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandMetricsSamples;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import rx.Observable;
import rx.functions.Func1;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.netflix.hystrix.contrib.rxnetty.metricsstream.HystrixMetricsStreamHandler.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(HystrixCommandMetrics.class)
public class HystrixMetricsStreamHandlerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static Collection<HystrixCommandMetrics> SAMPLE_HYSTRIX_COMMAND_METRICS =
            Collections.singleton(HystrixCommandMetricsSamples.SAMPLE_1);

    private int port;
    private HttpServer<ByteBuf, ByteBuf> server;
    private HttpClient<ByteBuf, ServerSentEvent> client;

    @Before
    public void setUp() throws Exception {
        server = createServer();

        client = RxNetty.<ByteBuf, ServerSentEvent>newHttpClientBuilder("localhost", port)
                .withNoConnectionPooling()
                .pipelineConfigurator(PipelineConfigurators.<ByteBuf>sseClientConfigurator())
                .build();

        mockStatic(HystrixCommandMetrics.class);
        expect(HystrixCommandMetrics.getInstances()).andReturn(SAMPLE_HYSTRIX_COMMAND_METRICS).anyTimes();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void testMetricsAreDeliveredAsSseStream() throws Exception {
        replayAll();

        Observable<ServerSentEvent> objectObservable = client.submit(HttpClientRequest.createGet(DEFAULT_HYSTRIX_PREFIX))
                .flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<? extends ServerSentEvent>>() {
                    @Override
                    public Observable<? extends ServerSentEvent> call(HttpClientResponse<ServerSentEvent> httpClientResponse) {
                        return httpClientResponse.getContent().take(1);
                    }
                });

        Object first = Observable.amb(objectObservable, Observable.timer(1000, TimeUnit.MILLISECONDS)).toBlocking().first();

        assertTrue("Expected SSE message", first instanceof ServerSentEvent);
        ServerSentEvent sse = (ServerSentEvent) first;
        JsonNode jsonNode = mapper.readTree(sse.getEventData());
        assertEquals("Expected hystrix key name", HystrixCommandMetricsSamples.SAMPLE_1.getCommandKey().name(), jsonNode.get("name").asText());
    }

    // We try a few times in case we hit into used port.
    private HttpServer<ByteBuf, ByteBuf> createServer() {
        Random random = new Random();
        Exception error = null;
        for (int i = 0; i < 3 && server == null; i++) {
            port = 10000 + random.nextInt(50000);
            try {
                return RxNetty.newHttpServerBuilder(port, new HystrixMetricsStreamHandler<>(
                        DEFAULT_HYSTRIX_PREFIX,
                        DEFAULT_INTERVAL,
                        new RequestHandler<ByteBuf, ByteBuf>() {  // Application handler
                            @Override
                            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                                return Observable.empty();
                            }
                        }
                )).build().start();
            } catch (Exception e) {
                error = e;
            }
        }
        throw new RuntimeException("Cannot initialize RxNetty server", error);
    }
}
