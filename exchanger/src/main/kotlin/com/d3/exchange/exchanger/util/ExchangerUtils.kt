/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.exchange.exchanger.util

private const val DELIMITER = '.'

/**
 * Normalizes a string to an asset precision
 * @return String {0-9}*.{0-9}[precision]
 */
fun respectPrecision(rawValue: String, precision: Int): String {
    if (precision == 0) {
        return rawValue.substringBefore(DELIMITER)
    }
    val substringAfter = rawValue.substringAfter(DELIMITER)
    val diff = substringAfter.length - precision
    return when {
        diff == 0 -> rawValue
        diff < 0 -> rawValue.plus("0".repeat(diff * (-1)))
        else -> rawValue.substringBefore(DELIMITER)
            .plus(DELIMITER)
            .plus(substringAfter.substring(0, precision))
    }
}

/**
 * Returns the first half of the hash with block height added
 */
fun normalizeTransactionHash(hash: String, height: Long) = hash.substring(0..32).toLowerCase() + "_$height"

/**
 * Returns a string with an underscore and index being added
 */
fun addCommandIndex(str: String, commandIndex: Int) = "${str}_${commandIndex}"
