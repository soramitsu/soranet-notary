/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package integration.sora

import com.d3.commons.model.NotaryGenericResponse
import com.d3.commons.registration.MappingRegistrationResponse
import com.d3.commons.sidechain.iroha.util.ModelUtil
import com.d3.commons.util.GsonInstance
import com.d3.commons.util.getRandomString
import com.d3.commons.util.toHexString
import integration.helper.IrohaIntegrationHelperUtil
import integration.registration.RegistrationServiceTestEnvironment
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * This test case tests Sora integration.
 * All accounts are created and have permissions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoraIntegrationTest {

    private val integrationHelper = IrohaIntegrationHelperUtil()

    private val registrationEnvironment = RegistrationServiceTestEnvironment(integrationHelper)

    private val domain = "sora"
    private val xorAsset = "xor#$domain"
    private val soraClientId = "xor@sora"

    private val gsonInstace = GsonInstance.get()

    init {
        GlobalScope.launch {
            registrationEnvironment.registrationInitialization.init()
        }

        runBlocking { delay(20_000) }
    }

    /**
     * Sora user can query her/his balance.
     * @given a client with 1334 xor on balance
     * @when the client queries the balance
     * @then the actual balance of 1334 xor is returned
     */
    @Test
    fun getXorBalanceTest() {
        val clientName = String.getRandomString(9)
        val clientId = "$clientName@$domain"

        val keypairAlice = Ed25519Sha3().generateKeypair()

        val res =
            registrationEnvironment.registerV1(clientName, keypairAlice.public.toHexString(), domain)

        assertEquals(200, res.statusCode)

        assertEquals(
            "0",
            integrationHelper.queryHelper.getAccountAsset(clientId, xorAsset).get()
        )

        integrationHelper.addIrohaAssetTo(clientId, xorAsset, "1334")
        assertEquals(
            "1334",
            integrationHelper.getAccountAssets(clientId).getOrDefault(xorAsset, "0")
        )
    }

    /**
     * Sora user transfer XOR to another client.
     * @given a client alice with 1334 xor and a client bob with 0 xor
     * @when alice transfer 1330 xor to bob
     * @then alice balance is 4 xor and bob balance is 1330 xor now
     */
    @Test
    fun transferSoraTest() {
        val aliceClientName = String.getRandomString(9)
        val aliceClientId = "$aliceClientName@$domain"
        val keypairAlice = Ed25519Sha3().generateKeypair()
        var res = registrationEnvironment.registerV1(
            aliceClientName,
            keypairAlice.public.toHexString(),
            domain
        )

        assertEquals(200, res.statusCode)
        val bobClientName = String.getRandomString(9)
        val bobClientId = "$bobClientName@$domain"
        val keypairBob = Ed25519Sha3().generateKeypair()
        res =
            registrationEnvironment.registerV1(bobClientName, keypairBob.public.toHexString(), domain)

        assertEquals(200, res.statusCode)

        integrationHelper.addIrohaAssetTo(aliceClientId, xorAsset, "12")

        integrationHelper.transferAssetIrohaFromClient(
            aliceClientId,
            keypairAlice,
            aliceClientId,
            bobClientId,
            xorAsset,
            "descr",
            "10"
        )

        assertEquals(
            "2",
            integrationHelper.getIrohaAccountBalance(aliceClientId, xorAsset)
        )
        assertEquals(
            "10",
            integrationHelper.getIrohaAccountBalance(bobClientId, xorAsset)
        )
    }

    /**
     * Sora distribution.
     *
     * @given sora account with 35 xor and alice with 0 xor and bob with 0 xor
     * @when distribute 17 to alice and 18 to bob
     * @then alice has 17 xor and bob has 18 xor
     */
    @Test
    fun distributeSoraTest() {
        val aliceClientName = String.getRandomString(9)
        val aliceClientId = "$aliceClientName@$domain"
        val keypairAlice = Ed25519Sha3().generateKeypair()
        var res = registrationEnvironment.registerV1(
            aliceClientName,
            keypairAlice.public.toHexString(),
            domain
        )

        assertEquals(200, res.statusCode)

        val bobClientName = String.getRandomString(9)
        val bobClientId = "$bobClientName@$domain"
        val keypairBob = Ed25519Sha3().generateKeypair()
        res =
            registrationEnvironment.registerV1(bobClientName, keypairBob.public.toHexString(), domain)

        assertEquals(200, res.statusCode)

        val soraKeyPair =
            ModelUtil.loadKeypair(
                "deploy/iroha/keys/xor@sora.pub",
                "deploy/iroha/keys/xor@sora.priv"
            ).get()
        integrationHelper.addIrohaAssetTo(soraClientId, xorAsset, "35")

        integrationHelper.transferAssetIrohaFromClient(
            soraClientId,
            soraKeyPair,
            soraClientId,
            aliceClientId,
            xorAsset,
            "descr",
            "17",
            quorum = 1
        )

        integrationHelper.transferAssetIrohaFromClient(
            soraClientId,
            soraKeyPair,
            soraClientId,
            bobClientId,
            xorAsset,
            "descr",
            "18",
            quorum = 1
        )

        assertEquals(
            "17",
            integrationHelper.getIrohaAccountBalance(aliceClientId, xorAsset)
        )
        assertEquals(
            "18",
            integrationHelper.getIrohaAccountBalance(bobClientId, xorAsset)
        )
    }

    /**
     * Test registration
     * @given Registration service is up and running
     * @when POST query is sent to register a user with `name` and `pubkey` and domain 'sora'
     * @then new account 'name@sora' is created in Iroha
     */
    @Test
    fun correctRegistration() {
        val name = String.getRandomString(9)
        val pubkey = Ed25519Sha3().generateKeypair().public.toHexString()

        val res = registrationEnvironment.registerV1(name, pubkey, domain)

        assertEquals(200, res.statusCode)

        val response = gsonInstace.fromJson(
            res.jsonObject.toString(),
            MappingRegistrationResponse::class.java
        )!!
        val actualClientId = response.responseMap["clientId"]
        assertEquals("$name@$domain", actualClientId)

        // ensure account is created
        try {
            integrationHelper.getAccount("$name@$domain")
        } catch (exc: Exception) {
            fail { "Expected no exceptions, but got $exc" }
        }
    }

    /**
     * @given registration service is up
     * @when send registration request with wrong key
     * @then error response is returned
     */
    @Test
    fun wrongKeyRegistration() {
        val name = String.getRandomString(9)
        val pubkey = "wrong_key"

        val res = registrationEnvironment.registerV1(name, pubkey, domain)

        assertEquals(200, res.statusCode)

        val response = gsonInstace.fromJson(
            res.jsonObject.toString(),
            NotaryGenericResponse::class.java
        )!!

        assertNotNull(response.status.code)
        assertNotNull(response.status.message)
    }

    /**
     * @given system is not initiated
     * @when system is initiated
     * @then initial supply of XOR is 1618033988749894848204586834
     */
    @Test
    fun initialSupplyXOR() {
        assertEquals(
            "1618033988.749894848204586834",
            integrationHelper.getIrohaAccountBalance(soraClientId, xorAsset)
        )
    }
}
