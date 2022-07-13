package org.acme.serverless.loanbroker.aggregator;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.acme.serverless.loanbroker.aggregator.model.BankQuote;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
@QuarkusTestResource(SinkMockTestResource.class)
public class QuotesAggregatorRouteTest {

        static {
                RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        }

        @Inject
        ObjectMapper objectMapper;

        @Inject
        QuotesRepositoryProcessor quotesRepository;

        @AfterEach
        void cleanUpRepository() {
                if (quotesRepository != null) {
                        quotesRepository.clear();
                }
        }

        @Test
        void verifyOneMessageAggregated() throws InterruptedException, JsonProcessingException {
                this.postMessageAndExpectSuccess(
                                new BankQuote("BankPremium", 4.655600086643112), "123");

                await()
                                .atMost(10, TimeUnit.SECONDS)
                                .with().pollInterval(3, TimeUnit.SECONDS)
                                .untilAsserted(() -> this.getQuotesAndAssert(1, "123"));
        }

        /**
         * @throws InterruptedException
         * @throws JsonProcessingException
         */
        @Test
        void verifyManyQuotesSingleInstanceMessageAggregated() throws InterruptedException, JsonProcessingException {
                this.postMessageAndExpectSuccess(
                                new BankQuote("BankPremium", 4.655600086643112), "123");
                this.postMessageAndExpectSuccess(
                                new BankQuote("BankStar", 5.4342645), "123");

                await()
                                .atMost(10, TimeUnit.SECONDS)
                                .with().pollInterval(3, TimeUnit.SECONDS)
                                .untilAsserted(() -> this.getQuotesAndAssert(2, "123"));
        }

        @Test
        void verifyManyQuotesManyInstancesMessageAggregated() throws InterruptedException, JsonProcessingException {
                this.postMessageAndExpectSuccess(
                                new BankQuote("BankPremium", 4.655600086643112), "123");
                this.postMessageAndExpectSuccess(
                                new BankQuote("BankPremium", 5.4342645), "456");

                await()
                                .atMost(10, TimeUnit.SECONDS)
                                .with().pollInterval(3, TimeUnit.SECONDS)
                                .untilAsserted(() -> this.getQuotesAndAssert(1, "123"));
                await()
                                .atMost(10, TimeUnit.SECONDS)
                                .with().pollInterval(3, TimeUnit.SECONDS)
                                .untilAsserted(() -> this.getQuotesAndAssert(1, "456"));

        }

        private void postMessageAndExpectSuccess(final BankQuote bankQuote, final String workflowInstanceId)
                        throws JsonProcessingException {

                final CloudEvent ce = CloudEventBuilder.v1()
                                .withId("123456")
                                .withType("kogito.serverless.loanbroker.bank.offer")
                                .withSource(URI.create("/local/tests"))
                                .withDataContentType(MediaType.APPLICATION_JSON)
                                .withData(PojoCloudEventData.wrap(bankQuote, objectMapper::writeValueAsBytes))
                                .withExtension("kogitoprocinstanceid", workflowInstanceId)
                                .build();

                RestAssured.given()
                                .header("Content-Type", "application/cloudevents+json")
                                // see:
                                // https://cloudevents.github.io/sdk-java/json-jackson.html#using-the-json-event-format
                                .body(EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                                                .serialize(ce))
                                .when()
                                .post("/")
                                .then()
                                .statusCode(200);
        }

        private void getQuotesAndAssert(final int quotesCount, final String workflowInstanceId) {
                RestAssured.given()
                                .get("/quotes/" + workflowInstanceId)
                                .then()
                                .statusCode(200)
                                .and()
                                .body("size()", Is.is(quotesCount));
        }
}
