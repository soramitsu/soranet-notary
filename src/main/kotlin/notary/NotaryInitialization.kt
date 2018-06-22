package notary

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.fanout
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import endpoint.RefundServerEndpoint
import endpoint.ServerInitializationBundle
import endpoint.eth.EthNotaryResponse
import endpoint.eth.EthRefund
import endpoint.eth.EthRefundRequest
import io.reactivex.Observable
import main.ConfigKeys
import mu.KLogging
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import sideChain.eth.EthChainHandler
import sideChain.eth.EthChainListener
import sideChain.iroha.IrohaChainHandler
import sideChain.iroha.IrohaChainListener
import sideChain.iroha.consumer.IrohaConsumerImpl
import sideChain.iroha.consumer.IrohaConverterImpl
import sideChain.iroha.consumer.IrohaKeyLoader
import sideChain.iroha.consumer.IrohaNetworkImpl
import java.math.BigInteger

/**
 * Class for notary instantiation
 * @param ethWalletsProvider - provides with white list of ethereum wallets
 * @param ethTokensProvider - provides with white list of ethereum ERC20 tokens
 */
class NotaryInitialization(
    val ethWalletsProvider: EthWalletsProvider = EthWalletsProviderImpl(),
    val ethTokensProvider: EthTokensProvider = EthTokensProviderImpl()
) {

    /**
     * Init notary
     */
    fun init(): Result<Unit, Exception> {
        logger.info { "Notary initialization" }
        return initEthChain()
            .fanout { initIrohaChain() }
            .map { (ethEvent, irohaEvents) ->
                initNotary(ethEvent, irohaEvents)
            }
            .flatMap { initIrohaConsumer(it) }
            .map { initRefund() }
    }

    /**
     * Init Ethereum chain listener
     * @return Observable on Ethereum sidechain events
     */
    private fun initEthChain(): Result<Observable<NotaryInputEvent>, Exception> {
        logger.info { "Init Eth chain" }

        val web3 = Web3j.build(HttpService(CONFIG[ConfigKeys.ethConnectionUrl]))
        /** List of all observable wallets */
        return ethWalletsProvider.getWallets()
            .fanout {
                /** List of all observable ERC20 tokens */
                ethTokensProvider.getTokens()
            }.flatMap { (wallets, tokens) ->
                val ethHandler = EthChainHandler(web3, wallets, tokens)
                EthChainListener(web3).getBlockObservable()
                    .map { observable ->
                        observable.flatMapIterable { ethHandler.parseBlock(it) }
                    }
            }
    }

    /**
     * Init Iroha chain listener
     * @return Observable on Iroha sidechain events
     */
    private fun initIrohaChain(): Result<Observable<NotaryInputEvent>, Exception> {
        logger.info { "Init Iroha chain" }
        return IrohaChainListener().getBlockObservable()
            .map { observable ->
                observable.flatMapIterable { IrohaChainHandler().parseBlock(it) }
            }
    }

    /**
     * Init Notary
     */
    private fun initNotary(ethEvents: Observable<NotaryInputEvent>, irohaEvents: Observable<NotaryInputEvent>): Notary {
        logger.info { "Init Notary notary" }
        return NotaryImpl(ethEvents, irohaEvents)
    }

    /**
     * Init Iroha consumer
     */
    private fun initIrohaConsumer(notary: Notary): Result<Unit, Exception> {
        logger.info { "Init Iroha consumer" }
        return IrohaKeyLoader.loadKeypair(CONFIG[ConfigKeys.pubkeyPath], CONFIG[ConfigKeys.privkeyPath])
            .map {
                val irohaConsumer = IrohaConsumerImpl(it)

                // Init Iroha Consumer pipeline
                notary.irohaOutput()
                    // convert from Notary model to Iroha model
                    // TODO rework Iroha batch transaction
                    .flatMapIterable { IrohaConverterImpl().convert(it) }
                    // convert from Iroha model to Protobuf representation
                    .map { irohaConsumer.convertToProto(it) }
                    .subscribe(
                        // send to Iroha network layer
                        { IrohaNetworkImpl().send(it) },
                        // on error
                        { logger.error { it } }
                    )
                Unit
            }
    }

    /**
     * Init refund endpoint
     */
    private fun initRefund() {
        logger.info { "Init Refund endpoint" }

        // TODO 18/05/2018, @muratovv: rework eth strategy with effective implementation
        RefundServerEndpoint(
            ServerInitializationBundle(CONFIG[ConfigKeys.refundPort], CONFIG[ConfigKeys.ethEndpoint]),
            mock {
                val request = any<EthRefundRequest>()
                on {
                    performRefund(request)
                } doReturn EthNotaryResponse.Successful(
                    "signature",
                    EthRefund("address", "coin", BigInteger.TEN)
                )
            })
    }

    /**
     * Logger
     */
    companion object : KLogging()
}
