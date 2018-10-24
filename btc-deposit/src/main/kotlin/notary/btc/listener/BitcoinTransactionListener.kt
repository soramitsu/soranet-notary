package notary.btc.listener

import io.reactivex.ObservableEmitter
import mu.KLogging
import org.bitcoinj.core.ScriptException
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.core.TransactionOutput
import sidechain.SideChainEvent
import java.math.BigInteger
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val BTC_ASSET_NAME = "btc"
private const val TWO_HOURS_MILLIS = 2 * 60 * 60 * 1000L;

class BitcoinTransactionListener(
    // Map full of Bitcoin addresses registered in Iroha in form (btc address -> Iroha client name)
    private val registeredAddresses: Map<String, String>,
    // Level of confidence aka depth of transaction. Recommend value is 6
    private val confidenceLevel: Int,
    // Source of Bitcoin deposit events
    private val emitter: ObservableEmitter<SideChainEvent.PrimaryBlockChainEvent>
) {

    fun onTransaction(tx: Transaction, blockTime: Date) {
        if (!hasRegisteredAddresses(tx)) {
            return
        }
        if (tx.confidence.depthInBlocks >= confidenceLevel) {
            //If tx has desired depth, we call function that handles it
            logger.info { "BTC was received. Tx: ${tx.hashAsString}" }
            handleTx(tx, blockTime)
        } else {
            /*
            Otherwise we will register listener, that listens to tx depth updates.
            Handling function will be called, if tx depth hits desired value
            */
            logger.info { "BTC was received, but it's not confirmed yet. Tx: ${tx.hashAsString}" }
            tx.confidence.addEventListener(ConfirmedTxListener(confidenceLevel, tx, blockTime, ::handleTx))

        }
    }

    //Checks if tx contains registered addresses in its outputs
    private fun hasRegisteredAddresses(tx: Transaction): Boolean {
        return registeredAddresses.keys.any { registeredBtcAddress ->
            tx.outputs.map { out ->
                outPutToBase58Address(out)
            }.contains(registeredBtcAddress)
        }
    }

    private fun handleTx(tx: Transaction, blockTime: Date) {
        tx.outputs.forEach { output ->
            val btcAddress = outPutToBase58Address(output)
            logger.info { "Tx ${tx.hashAsString} has output address $btcAddress" }
            val irohaAccount = registeredAddresses[btcAddress]
            if (irohaAccount != null) {
                val event = SideChainEvent.PrimaryBlockChainEvent.OnPrimaryChainDeposit(
                    tx.hashAsString,
                    /*
                    Due to Iroha time restrictions, tx time must be in range [current time - 1 day; current time + 5 min],
                    while Bitcoin block time must be in range [median time of last 11 blocks; network time + 2 hours].
                    Given these restrictions, block time may be more than 5 minutes ahead of current time.
                    Subtracting 2 hours is just a simple workaround of this problem.
                    */
                    BigInteger.valueOf(blockTime.time - TWO_HOURS_MILLIS),
                    irohaAccount,
                    BTC_ASSET_NAME,
                    output.value.value.toString(),
                    ""
                )
                logger.info { "BTC deposit event(tx ${tx.hashAsString}, amount ${output.value.value}) was created. Related client is $irohaAccount. " }
                emitter.onNext(event)
            }
        }

    }

    private class ConfirmedTxListener(
        private val confidenceLevel: Int,
        private val tx: Transaction,
        private val blockTime: Date,
        private val txHandler: (Transaction, Date) -> Unit
    ) : TransactionConfidence.Listener {
        private val processed = AtomicBoolean()
        override fun onConfidenceChanged(
            confidence: TransactionConfidence,
            reason: TransactionConfidence.Listener.ChangeReason
        ) {
            /*
            Due to bitoinj library threading issues, we can miss an event of 'depthInBlocks'
            being exactly 'confidenceLevel'. So we check it to be at least 'confidenceLevel'.
            This leads D3 to handle the same transaction many times. This is why we use a special
            flag to check if it has been handled already.
            */
            if (confidence.depthInBlocks >= confidenceLevel
                && processed.compareAndSet(false, true)
            ) {
                logger.info { "BTC tx ${tx.hashAsString} was confirmed" }
                confidence.removeEventListener(this)
                txHandler(tx, blockTime)
            }
        }
    }

    //Safely takes base58 encoded address from tx output
    private fun outPutToBase58Address(output: TransactionOutput): String {
        try {
            return output.scriptPubKey.getToAddress(output.params).toBase58()
        } catch (expected: ScriptException) {
            return "[undefined]"
        }
    }

    /**
     * Logger
     */
    companion object : KLogging()
}