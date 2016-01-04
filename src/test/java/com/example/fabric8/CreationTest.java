package com.example.fabric8;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;


/**
 * To run this test from your IDE, make sure that you define a launch configuration (or whatever similar thing
 * they have in Eclipse) with the following system property defined:
 *      -DKRYLOV_HOST_BASE_URI=http://{krylovHostNameOrIP}/api/
 */
public class CreationTest {
    static String BASE_URI;
    static final String TEST_USER="testUser";

    static final String KUBE_SERVER = "http://localhost:8080";  // change according to where your server lives

    ClusterManager manager = new ClusterManager( new KubeClient(KUBE_SERVER));

    @BeforeClass
    public static void onceExecutedBeforeAll() throws Exception {
        System.out.println("we are now in the before...");
    }

    @Test
    public void createLoadBalancedNginxTest() throws Exception {
        manager.createComponent(getJsonString("nginx.json"), "default");
        System.out.println("DONE with the pod");
        manager.createComponent(getJsonString("svc.json"), "default");
        System.out.println("DONE with the service");
    }

    private String getJsonString(String filename) throws IOException {
        String json;InputStream nginx = this.getClass().getClassLoader().getResourceAsStream(filename);
        return IOUtils.toString(nginx);
    }
}

