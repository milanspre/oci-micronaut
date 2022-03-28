package api;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.support.TestPropertyProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

abstract class AbstractDatabaseServiceTest implements TestPropertyProvider {

    static OracleContainer oracleContainer;
    static GenericContainer<?> serviceContainer;

    protected static DockerImageServiceType defaultDockerImageServiceType = DockerImageServiceType.GRAALVM;

    @AfterAll
    static void cleanup() {
        oracleContainer.stop();
        serviceContainer.stop();
    }

    @NonNull
    @Override
    public Map<String, String> getProperties() {
        oracleContainer = new OracleContainer("gvenzl/oracle-xe:slim")
                .usingSid()
                .withNetwork(Network.SHARED)
                .withNetworkAliases("oracledb");
        oracleContainer.start();

        try (Connection connection = DriverManager.getConnection(
                oracleContainer.getJdbcUrl(),
                "system",
                oracleContainer.getPassword()
        )) {
            connection.prepareStatement("GRANT SODA_APP TO " + oracleContainer.getUsername()).execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to setup SODA: " + e.getMessage(), e);
        }

        serviceContainer = initService();
        serviceContainer.start();
        return Map.of(
                "micronaut.http.services.mushop-" + getServiceId() + ".url", "http://localhost:" + serviceContainer.getFirstMappedPort()
        );
    }

    protected GenericContainer<?> initService() {
        Map serviceConfig = Map.of(
                "DATASOURCES_DEFAULT_URL", oracleContainer.getJdbcUrl(),
                "DATASOURCES_DEFAULT_USERNAME", oracleContainer.getUsername(),
                "DATASOURCES_DEFAULT_PASSWORD", oracleContainer.getPassword()
        );
        return new GenericContainer<>(composeServiceDockerImage())
                .withExposedPorts(getServiceExposedPort())
                .withEnv(serviceConfig);
    }

    protected DockerImageName composeServiceDockerImage() {
        return DockerImageName.parse("iad.ocir.io/cloudnative-devrel/micronaut-showcase/mushop/" + getServiceId() + "-" + defaultDockerImageServiceType.name().toLowerCase() + ":" + getServiceVersion());
    }

    protected int getServiceExposedPort() {
        return 8080;
    }

    protected abstract String getServiceVersion();

    protected abstract String getServiceId();

    @Client("/api")
    interface LoginClient {
        @Post("/login")
        HttpResponse<?> login(BasicAuth basicAuth);
    }

    enum DockerImageServiceType {
        GRAALVM,
        OPENJDK,
        NATIVE
    }
}
