/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration

import com.d3.commons.registration.RegistrationServiceEndpoint.Companion.CLIENT_ID
import com.d3.commons.util.getRandomString
import com.d3.commons.util.toHexString
import integration.helper.D3_DOMAIN
import integration.helper.DockerComposeStarter
import integration.helper.IrohaIntegrationHelperUtil
import integration.registration.RegistrationServiceTestEnvironment
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DockerComposeStarter::class)
class NotaryRegistrationTest {

    /** Integration tests util */
    private val integrationHelper = IrohaIntegrationHelperUtil()

    private val registrationServiceEnvironment =
        RegistrationServiceTestEnvironment(integrationHelper)

    init {
        registrationServiceEnvironment.registrationInitialization.init()

        runBlocking { delay(35_000) }
    }

    @AfterAll
    fun closeEnvironments() {
        registrationServiceEnvironment.close()
    }

    /**
     * Test healthcheck
     * @given Registration service is up and running
     * @when GET query sent to healthcheck port
     * @then {"status":"UP"} is returned
     */
    @Test
    fun healthcheck() {
        val res =
            khttp.get("http://127.0.0.1:${registrationServiceEnvironment.registrationConfig.port}/actuator/health")
        assertEquals(200, res.statusCode)
        assertEquals("{\"status\":\"UP\"}", res.text)
    }

    /**
     * Test registration
     * @given Registration service is up and running
     * @when POST query is sent to register a user with `name` and `pubkey`
     * @then new account is created in Iroha
     */
    @Test
    fun correctRegistration() {
        val name = String.getRandomString(7)
        val pubkey = Ed25519Sha3().generateKeypair().public.toHexString()

        val res = registrationServiceEnvironment.registerV1(name, pubkey)

        assertEquals(200, res.statusCode)
        assertTrue(registrationServiceEnvironment.notaryClientsProvider.isClient("$name@$D3_DOMAIN").get())
    }

    /**
     * Test registration
     * @given Registration service is up and running
     * @when POST query is sent to register a user with `name` and `pubkey` using old endpoint
     * @then new account is created in Iroha
     */
    @Test
    fun correctOldRegistration() {
        val name = String.getRandomString(7)
        val pubkey = Ed25519Sha3().generateKeypair().public.toHexString()
        val accountId = "$name@$D3_DOMAIN"

        val res = registrationServiceEnvironment.register(name, pubkey)

        assertEquals(200, res.statusCode)
        assertEquals("{\"$CLIENT_ID\":\"$accountId\"}", res.text)
        assertTrue(registrationServiceEnvironment.notaryClientsProvider.isClient(accountId).get())
    }

    /**
     * Test registration
     * @given Registration service is up and running
     * @when POST query is sent to register a user with `name` and `pubkey` where user with 'name' already exists
     * @then Ok response returned
     */
    @Test
    fun doubleRegistration() {
        val name = String.getRandomString(7)
        val pubkey = Ed25519Sha3().generateKeypair().public.toHexString()

        // register client
        var res = registrationServiceEnvironment.registerV1(name, pubkey)
        assertEquals(200, res.statusCode)
        assertTrue(registrationServiceEnvironment.notaryClientsProvider.isClient("$name@$D3_DOMAIN").get())

        // try to register with the same name
        res = registrationServiceEnvironment.registerV1(name, pubkey)
        assertEquals(200, res.statusCode)
        assertTrue(registrationServiceEnvironment.notaryClientsProvider.isClient("$name@$D3_DOMAIN").get())
    }
}
