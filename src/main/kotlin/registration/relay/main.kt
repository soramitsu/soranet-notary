@file:JvmName("DeployRelay")

package registration.relay

import com.github.kittinunf.result.failure
import com.github.kittinunf.result.flatMap
import com.natpryce.konfig.ConfigurationProperties
import mu.KLogging
import sidechain.iroha.IrohaInitialization

/** Configuration parameters for notary instance */
val CONFIG = ConfigurationProperties.fromResource("defaults.properties")

/**
 * Entry point for deployment of relay smart contracts that will be used in client registration.
 * The main reason to move the logic of contract deployment to separate executable is that it takes too much time and
 * thus it should be done in advance.
 */
fun main(args: Array<String>) {
    val logger = KLogging()

    // TODO a.chernyshov - think about automatization of trigger and obtaining master address
    val num = 10
    val master = "0x00Bd138aBD70e2F00903268F3Db08f2D25677C9e"

    IrohaInitialization.loadIrohaLibrary()
        .flatMap { RelayRegistration().deploy(num, master) }
        .failure {
            logger.logger.error { it }
            System.exit(1)
        }

}
