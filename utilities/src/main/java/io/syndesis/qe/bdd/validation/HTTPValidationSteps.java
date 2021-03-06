package io.syndesis.qe.bdd.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.syndesis.qe.accounts.Account;
import io.syndesis.qe.resource.impl.HTTPEndpoints;
import io.syndesis.qe.utils.AccountUtils;
import io.syndesis.qe.utils.HTTPResponse;
import io.syndesis.qe.utils.HttpUtils;
import io.syndesis.qe.utils.OpenShiftUtils;
import io.syndesis.qe.utils.TestUtils;
import io.syndesis.qe.wait.OpenShiftWaitUtils;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.openshift.api.model.DeploymentConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HTTPValidationSteps {
    // Static to have this lpf shared between tests
    private static LocalPortForward localPortForward;

    @When("^clear endpoint events$")
    public void clear() {
        if (localPortForward != null) {
            TestUtils.terminateLocalPortForward(localPortForward);
        }
        Optional<Pod> pod = OpenShiftUtils.getPodByPartialName("endpoints");
        assertThat(pod.isPresent()).isTrue();
        localPortForward = TestUtils.createLocalPortForward(pod.get(), 8080, 28080);

        // Clear all events
        HttpUtils.doDeleteRequest("http://localhost:28080/clearEvents");
    }

    @Then("^verify that endpoint \"([^\"]*)\" was executed$")
    public void verifyThatEndpointWasExecuted(String method) {
        verify(method, false);
    }

    @Then("^verify that endpoint \"([^\"]*)\" was executed once$")
    public void verifyThatEndpointWasExecutedOnce(String method) {
        verify(method, true);
    }

    private void verify(String method, boolean once) {
        // Let the integration running
        TestUtils.sleepIgnoreInterrupt(30000L);
        // Get new events
        HTTPResponse r = HttpUtils.doGetRequest("http://localhost:28080/events");
        Map<Long, String> events = new Gson().fromJson(r.getBody(), Map.class);

        if (once) {
            assertThat(events).size().isEqualTo(1);
        } else {
            assertThat(events).size().isGreaterThanOrEqualTo(5);
        }
        for (String event : events.values()) {
            assertThat(method.equals(event));
        }
    }

    @Then("^verify that after \"([^\"]*)\" seconds there were \"([^\"]*)\" calls$")
    public void verifyThatAfterSecondsWasCalls(double seconds, int calls) {
        clear();
        TestUtils.sleepIgnoreInterrupt((long) seconds * 1000);
        HTTPResponse r = HttpUtils.doGetRequest("http://localhost:28080/events");
        Map<Long, String> events = new Gson().fromJson(r.getBody(), Map.class);
        assertThat(events).size().isEqualTo(calls);
    }

    @When("send get request using {string} and {string} path")
    public void sendGetRequestUsingAndPath(String account, String path) {
        final Account a = AccountUtils.get(account);
        HttpUtils.doGetRequest(a.getProperty("baseUrl") + path);
    }

    @When("^configure keystore in (HTTP|HTTPS) integration dc$")
    public void configureKeystore(String protocol) {
        if ("HTTP".equals(protocol)) {
            return;
        }
        try {
            OpenShiftWaitUtils.waitFor(() -> !OpenShiftUtils.getInstance().deploymentConfigs().withLabel("syndesis.io/type", "integration").list()
                .getItems().isEmpty(), 300000L);
        } catch (TimeoutException | InterruptedException e) {
            fail("Unable to find integration deployment config after 5 minutes");
        } catch (Exception e) {
            // ignore
        }
        List<DeploymentConfig> integrationDcs =
            OpenShiftUtils.getInstance().deploymentConfigs().withLabel("syndesis.io/type", "integration").list().getItems();
        assertThat(integrationDcs).as("There should be only one integration deployment config").hasSize(1);
        DeploymentConfig dc = integrationDcs.get(0);
        //@formatter:off
        OpenShiftUtils.getInstance().deploymentConfigs().withName(dc.getMetadata().getName()).edit()
            .editSpec()
                .editTemplate()
                    .editSpec()
                        .addNewVolume()
                            .withName("keystore")
                            .withNewSecret()
                                .withSecretName(HTTPEndpoints.KEYSTORE_SECRET_NAME)
                            .endSecret()
                        .endVolume()
                        .editFirstContainer()
                            .addNewVolumeMount()
                                .withNewMountPath("/opt/jboss/")
                                .withName("keystore")
                            .endVolumeMount()
                            .addToEnv(new EnvVar(
                                "JAVA_OPTIONS",
                                "-Djavax.net.ssl.trustStore=/opt/jboss/keystore.p12 -Djavax.net.ssl.trustStorePassword=tomcat -Djavax.net.ssl.trustStoreAlias=tomcat",
                                null
                            ))
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .done();
        //@formatter:on
    }
}
