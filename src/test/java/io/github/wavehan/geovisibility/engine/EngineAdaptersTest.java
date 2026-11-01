package io.github.wavehan.geovisibility.engine;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EngineAdaptersTest {

    static final long[] NO_BACKOFF = {};

    @Test
    void compatParsesAnswerAndTopLevelCitations() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("https://fake.test/api/paas/v4/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Alpha is great"}}],
                         "citations":["https://alpha.com/a","https://reddit.com/r/x"]}""",
                        MediaType.APPLICATION_JSON));
        var e = new CompatEngine("zhipu", "https://fake.test/api/paas/v4", "glm-4-flash",
                "k", Map.of("tools", List.of()), builder.build(), NO_BACKOFF);
        var ans = e.ask("best widget");
        assertEquals("Alpha is great", ans.answer());
        assertEquals(2, ans.citations().size());
    }

    @Test
    void responsesParsesUrlCitationAnnotations() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("https://fake.test/v1/responses"))
                .andRespond(withSuccess("""
                        {"output":[{"type":"message","content":[
                          {"type":"output_text","text":"Beta wins",
                           "annotations":[{"type":"url_citation","url":"https://beta.com/x"}]}]}]}""",
                        MediaType.APPLICATION_JSON));
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "deepseek-v4-flash",
                "k", builder.build(), NO_BACKOFF);
        var ans = e.ask("q");
        assertEquals("Beta wins", ans.answer());
        assertEquals(List.of("https://beta.com/x"), ans.citations());
    }

    @Test
    void httpErrorBecomesEngineExceptionWithTruncatedBody() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("https://fake.test/v1/responses"))
                .andRespond(withServerError().body("{\"error\":\"" + "x".repeat(5000) + "\"}"));
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "m",
                "k", builder.build(), NO_BACKOFF);
        var ex = assertThrows(EngineException.class, () -> e.ask("q"));
        assertEquals(500, ex.status());
        assertTrue(ex.body().length() <= 200);
    }

    @Test
    void retriesThenFails() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        // 1 initial attempt + 2 retries (backoff length 2) = 3 requests, then EngineException
        server.expect(requestTo("https://fake.test/v1/responses")).andRespond(withServerError());
        server.expect(requestTo("https://fake.test/v1/responses")).andRespond(withServerError());
        server.expect(requestTo("https://fake.test/v1/responses")).andRespond(withServerError());
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "m",
                "k", builder.build(), new long[]{0, 0});
        assertThrows(EngineException.class, () -> e.ask("q"));
        server.verify();
    }

    @Test
    void retriesOnNetworkErrorThenSucceeds() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        // first attempt: network-level failure (ResourceAccessException) -> must retry
        server.expect(requestTo("https://fake.test/v1/responses"))
                .andRespond(org.springframework.test.web.client.response
                        .MockRestResponseCreators.withException(new java.io.IOException("reset")));
        server.expect(requestTo("https://fake.test/v1/responses"))
                .andRespond(withSuccess("""
                        {"output":[{"type":"message","content":[
                          {"type":"output_text","text":"recovered"}]}]}""",
                        MediaType.APPLICATION_JSON));
        var e = new ResponsesEngine("deepseek", "https://fake.test/v1", "m",
                "k", builder.build(), new long[]{0});
        assertEquals("recovered", e.ask("q").answer());
        server.verify();
    }
}
