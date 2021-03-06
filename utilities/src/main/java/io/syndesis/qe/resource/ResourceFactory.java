package io.syndesis.qe.resource;

import io.syndesis.qe.test.InfraFail;
import io.syndesis.qe.wait.OpenShiftWaitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceFactory {
    private static List<Resource> createdResources = new ArrayList<>();

    /**
     * Gets (or creates and gets) the instance of given class and calls its the deploy method.
     * @param clazz class to create
     * @param <T> type
     */
    public static <T extends Resource> void create(Class<T> clazz) {
        get(clazz).deploy();
        try {
            log.info("Waiting until " + clazz.getSimpleName() + " is ready");
            OpenShiftWaitUtils.waitFor(() -> get(clazz).isReady(), 10 * 60000L);
        } catch (TimeoutException | InterruptedException e) {
            InfraFail.fail("Wait for " + clazz.getSimpleName() + " failed", e);
        }
    }

    public static <T extends Resource> void destroy(Class<T> clazz) {
        get(clazz).undeploy();
    }

    /**
     * Gets (or creates and gets) the instance of given class and returns it.
     * @param clazz class to create
     * @param <T> type
     * @return instance of given class
     */
    public static <T extends Resource> T get(Class<T> clazz) {
        Optional<Resource> oExtRes = createdResources.stream().filter(clazz::isInstance).findAny();
        if (oExtRes.isPresent()) {
            log.debug("Returning previously created instance of " + clazz.getSimpleName());
            return (T) oExtRes.get();
        } else {
            log.info("Creating a new instance of " + clazz.getSimpleName());
            T instance = null;
            try {
                instance = clazz.newInstance();
                createdResources.add(instance);
                return instance;
            } catch (Exception e) {
                InfraFail.fail("Unable to create instance of " + clazz.getSimpleName());
            }
            return instance;
        }
    }

    /**
     * Calls the undeploy method on all instances created by the factory.
     */
    public static void cleanup() {
        createdResources.forEach(created -> {
            if (created.isDeployed()) {
                log.info("Undeploying resource " + created.getClass().getSimpleName());
                created.undeploy();
            }
        });
    }
}
