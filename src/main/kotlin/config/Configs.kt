package config

import com.jdiazcano.cfg4k.loaders.PropertyConfigLoader
import com.jdiazcano.cfg4k.providers.ProxyConfigProvider
import com.jdiazcano.cfg4k.sources.ClasspathConfigSource

/**
 * Iroha configurations
 */
interface IrohaConfig {
    val hostname: String
    val port: Int
    val creator: String
    val pubkeyPath: String
    val privkeyPath: String
}

/**
 * Ethereum configurations
 */
interface EthereumConfig {
    val url: String
    val credentialsPath: String
    val gasPrice: Long
    val gasLimit: Long
    val confirmationPeriod: Long
}

/**
 * Bitcoin configurations
 */
interface BitcoinConfig {
    //Path of wallet file
    val walletPath: String
    //Path of block storage folder
    val blockStoragePath: String
    //Depth of transactions in BTC blockchain
    val confidenceLevel: Int
}

/**
 * Ethereum passwords
 */
interface EthereumPasswords {
    val credentialsPassword: String
    val nodeLogin: String
    val nodePassword: String
}

/**
 * Load configs from Java properties
 */
fun <T : Any> loadConfigs(prefix: String, type: Class<T>, filename: String = "/defaults.properties"): T {
    val loader = PropertyConfigLoader(ClasspathConfigSource(filename))
    val provider = ProxyConfigProvider(loader)
    return provider.bind(prefix, type)
}
