/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.commons.notary.endpoint

/**
 * Class responsible for creating refunds from Iroha to side chains
 *
 * @param Request type of custodian's request
 * @param NotaryResponse type of notary response
 */
interface Refund<Request, NotaryResponse> {

    /**
     * Perform rollback for side chain
     */
    fun performRefund(request: Request): NotaryResponse
}
