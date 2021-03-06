package io.syndesis.qe.utils.jira;

import io.syndesis.qe.accounts.Account;
import io.syndesis.qe.utils.AccountUtils;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@Lazy
public class JiraClientFactory {

    @Bean
    public JiraRestClient getJiraRestClient() throws URISyntaxException {
        Account jiraAccount = AccountUtils.get("Jira");
        String jiraUrl = jiraAccount.getProperty("jiraurl");
        return new AsynchronousJiraRestClientFactory().create(new URI(jiraUrl), new OAuthJiraAuthenticationHandler());
    }
}
