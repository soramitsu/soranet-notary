/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.notifications.handler

import com.d3.commons.provider.NotaryClientsProvider
import com.d3.commons.sidechain.iroha.FEE_ROLLBACK_DESCRIPTION
import com.d3.notifications.config.NotificationsConfig
import com.d3.notifications.event.DepositTransferEvent
import com.d3.notifications.queue.EventsQueue
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Handler that handles deposit events
 */
@Component
class DepositCommandHandler(
    private val notificationsConfig: NotificationsConfig,
    private val notaryClientsProvider: NotaryClientsProvider,
    private val eventsQueue: EventsQueue
) : CommandHandler() {
    override fun handle(commandWithTx: CommandWithTx) {
        val transferAsset = commandWithTx.command.transferAsset
        val transferNotifyEvent = DepositTransferEvent(
            accountIdToNotify = transferAsset.destAccountId,
            amount = BigDecimal(transferAsset.amount),
            assetName = transferAsset.assetId,
            from = transferAsset.description,
            id = Utils.toHex(Utils.hash(commandWithTx.tx)) + "_deposit",
            time = commandWithTx.tx.payload.reducedPayload.createdTime
        )
        logger.info("Notify deposit $transferNotifyEvent")
        eventsQueue.enqueue(transferNotifyEvent)
    }

    override fun ableToHandle(commandWithTx: CommandWithTx) = safeCheck {
        if (!commandWithTx.command.hasTransferAsset()) {
            return false
        }
        val transferAsset = commandWithTx.command.transferAsset

        val depositSign =
            (transferAsset.srcAccountId == notificationsConfig.ethDepositAccount || transferAsset.srcAccountId == notificationsConfig.btcDepositAccount)
                    && transferAsset.destAccountId != notificationsConfig.transferBillingAccount
                    && transferAsset.destAccountId != notificationsConfig.withdrawalBillingAccount
                    && notaryClientsProvider.isClient(transferAsset.destAccountId).get()
        return depositSign && !isRollbackSign(transferAsset) && transferAsset.description != FEE_ROLLBACK_DESCRIPTION
    }

    companion object : KLogging()
}