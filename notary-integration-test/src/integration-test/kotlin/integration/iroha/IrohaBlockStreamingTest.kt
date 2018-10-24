package integration.iroha

import com.github.kittinunf.result.map
import config.loadConfigs
import integration.TestConfig
import integration.helper.IntegrationHelperUtil
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sidechain.iroha.IrohaChainListener
import sidechain.iroha.consumer.IrohaConsumerImpl
import sidechain.iroha.consumer.IrohaNetworkImpl
import sidechain.iroha.util.ModelUtil.getCurrentTime
import sidechain.iroha.util.ModelUtil.getModelTransactionBuilder
import java.util.concurrent.TimeUnit

/**
 * Note: Requires Iroha is running.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IrohaBlockStreamingTest {

    /** Test configurations */
    private val testConfig = loadConfigs("test", TestConfig::class.java, "/test.properties")

    private val integrationHelper = IntegrationHelperUtil()

    private val testCredential = integrationHelper.testCredential

    private val creator = testCredential.accountId

    private val irohaNetwork = IrohaNetworkImpl(testConfig.iroha.hostname, testConfig.iroha.port)

    private val listener = IrohaChainListener(
        testConfig.iroha.hostname,
        testConfig.iroha.port,
        testCredential
    )

    @BeforeAll
    fun setUp() {
        System.loadLibrary("irohajava")
    }

    @AfterAll
    fun dropDown() {
        integrationHelper.close()
        irohaNetwork.close()
        listener.close()
    }

    /**
     * @given Iroha running
     * @when new tx is sent to Iroha
     * @then block arrived to IrohaListener
     */
    @Test
    fun irohaStreamingTest() {
        var cmds = listOf<iroha.protocol.Commands.Command>()

        listener.getBlockObservable()
            .map { obs ->
                obs.map { block ->
                    cmds = block.payload.transactionsList
                        .flatMap {
                            it.payload.reducedPayload.commandsList
                        }
                }.subscribeOn(Schedulers.io()).subscribe()
            }

        val utx = getModelTransactionBuilder()
            .creatorAccountId(creator)
            .createdTime(getCurrentTime())
            .setAccountDetail(creator, "test", "test")
            .build()

        IrohaConsumerImpl(testCredential, irohaNetwork).sendAndCheck(utx)
        runBlocking {
            delay(5000, TimeUnit.MILLISECONDS)
        }

        assertEquals(1, cmds.size)
        assertEquals(creator, cmds.first().setAccountDetail.accountId)
        assertEquals("test", cmds.first().setAccountDetail.key)
        assertEquals("test", cmds.first().setAccountDetail.value)
    }

    /**
     * @given Iroha running
     * @when new tx is sent to Iroha
     * @then block arrived to IrohaListener and returned as coroutine
     */
    @Test
    fun irohaGetBlockTest() {
        val block = async {
            listener.getBlock()
        }

        val utx = getModelTransactionBuilder()
            .creatorAccountId(creator)
            .createdTime(getCurrentTime())
            .setAccountDetail(creator, "test", "test")
            .build()

        IrohaConsumerImpl(testCredential, irohaNetwork).sendAndCheck(utx)


        val bl = runBlocking {
            block.await()
        }

        val cmds = bl.payload.transactionsList
            .flatMap {
                it.payload.reducedPayload.commandsList
            }
        assertEquals(1, cmds.size)
        assertEquals(creator, cmds.first().setAccountDetail.accountId)
        assertEquals("test", cmds.first().setAccountDetail.key)
        assertEquals("test", cmds.first().setAccountDetail.value)
    }
}