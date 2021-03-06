package io.syndesis.qe.rest.tests.steps.extension;

import static org.assertj.core.api.Assertions.assertThat;

import io.syndesis.qe.endpoints.ExtensionsEndpoint;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

import cucumber.api.java.en.Given;
import io.cucumber.datatable.DataTable;

public class ExtensionHandler {
    @Autowired
    private ExtensionsEndpoint extensionsEndpoint;

    @Given("^import extensions from syndesis-extensions folder$")
    public void importExtensionsFromSyndesisExtensionsFolder(DataTable properties) {
        List<String> extensions = properties.asList(String.class);
        for (String ext : extensions) {
            String defaultPath = "../syndesis-extensions/" + ext + "/target/";
            File[] files = new File(defaultPath).listFiles((dir, name) -> !name.contains("original") && name.endsWith(".jar"));
            assertThat(files).hasSize(1).doesNotContainNull();
            importExtensionFromPath(files[0].getAbsolutePath());
        }
    }

    @Given("^import extension from path \"([^\"]*)\"$")
    public void importExtensionFromPath(String path) {
        File extension = new File(path);
        assertThat(extension).exists();
        extensionsEndpoint.installExtension(extensionsEndpoint.uploadExtension(extension));
    }
}
