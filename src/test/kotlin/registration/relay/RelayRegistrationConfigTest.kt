package registration.relay

import config.loadConfigs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RelayRegistrationConfigTest {

    /**
     * @given a config with correct default values
     * @when config is loaded
     * @then relayRegistrationConfig object is returned and all configuration parameters are set
     */
    @Test
    fun allSetTest() {
        val relayRegistrationConfig = loadConfigs("relay-registration", RelayRegistrationConfig::class.java)

        assertEquals(10, relayRegistrationConfig.number)
        assertEquals("notary_red@notary", relayRegistrationConfig.notaryIrohaAccount)
        assertEquals("0x1a0e2560d27dccc12f3774332602a724802a5e6a", relayRegistrationConfig.ethMasterWallet)

        assertEquals("localhost", relayRegistrationConfig.iroha.hostname)
        assertEquals(50051, relayRegistrationConfig.iroha.port)
        assertEquals("registration_service_red@notary", relayRegistrationConfig.iroha.creator)
        assertEquals("deploy/iroha/keys/admin@notary.pub", relayRegistrationConfig.iroha.pubkeyPath)
        assertEquals("deploy/iroha/keys/admin@notary.priv", relayRegistrationConfig.iroha.privkeyPath)

        assertEquals("http://51.15.84.132:8545", relayRegistrationConfig.ethereum.url)
        assertEquals("deploy/ethereum/keys/user.key", relayRegistrationConfig.ethereum.credentialsPath)
        assertEquals(1, relayRegistrationConfig.ethereum.gasPrice)
        assertEquals(999999, relayRegistrationConfig.ethereum.gasLimit)
    }
}