package io.micronaut.configuration.acme

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import org.shredzone.acme4j.Account
import org.shredzone.acme4j.AccountBuilder
import org.shredzone.acme4j.Session
import org.shredzone.acme4j.Status
import org.shredzone.acme4j.util.KeyPairUtils
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.security.KeyPair

public class AcmeBaseSpec extends Specification{
    public static final String EXPECTED_DOMAIN = "host.testcontainers.internal"
    public static final int EXPECTED_PORT = 8443
    @Shared
    GenericContainer certServerContainer =
            new GenericContainer("letsencrypt/pebble:latest")
                    .withEnv("PEBBLE_VA_ALWAYS_VALID", "1")
                    .withExposedPorts(14000)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer

    @Shared
    @AutoCleanup
    HttpClient client


    @Shared
    @ClassRule
    TemporaryFolder temporaryFolder

    @Shared
    File certFolder

    @Shared
    StringWriter accountKeyPairWriter

    @Shared
    StringWriter domainKeyPairWriter

    @Shared
    String acmeServerUrl

    def setupSpec() {
        Testcontainers.exposeHostPorts(EXPECTED_PORT)

        certServerContainer.start()

        // Create a new keypair to register the account with
        KeyPair keyPair = KeyPairUtils.createKeyPair(2048)
        accountKeyPairWriter = new StringWriter()
        KeyPairUtils.writeKeyPair(keyPair, accountKeyPairWriter)

        // Create a new keypair to use for the domain
        KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048)
        domainKeyPairWriter = new StringWriter()
        KeyPairUtils.writeKeyPair(domainKeyPair, domainKeyPairWriter)

        acmeServerUrl = "acme://pebble/${certServerContainer.containerIpAddress}:${certServerContainer.getMappedPort(14000)}"

        // Create an account with the acme server
        Session session = new Session(acmeServerUrl)
        Account createNewAccount = new AccountBuilder()
                .agreeToTermsOfService()
                .addEmail("test@micronaut.io")
                .useKeyPair(keyPair)
                .create(session)
        assert createNewAccount.status == Status.VALID

        certFolder = temporaryFolder.newFolder()
        embeddedServer = ApplicationContext.run(EmbeddedServer,
                getConfiguration(),
        "test")

        client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())
    }

    Map<String, Object> getConfiguration() {
        [
                "micronaut.ssl.enabled"              : true,
                "micronaut.server.host"              : EXPECTED_DOMAIN,
                "micronaut.ssl.acme.tos.agree"       : true,
                "micronaut.ssl.acme.cert.output.path": certFolder.toString(),
                "micronaut.ssl.acme.domain.keypair"  : domainKeyPairWriter.toString(),
                "micronaut.ssl.acme.account.keypair" : accountKeyPairWriter.toString(),
                'micronaut.ssl.acme.server.url'      : acmeServerUrl,
                'micronaut.ssl.acme.enabled'         : true,
                'micronaut.ssl.acme.order.pause.ms'  : 1000,
                'micronaut.ssl.acme.auth.pause.ms'   : 1000
        ]
    }
}