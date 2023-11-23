package com.tangem.crypto.hdWallet

/**
 * Created by Anton Zhilenkov on 06/08/2021.
 */
class BIP44(
    val coinType: Long,
    val account: Long,
    val change: Chain,
    val addressIndex: Long,
) {

    constructor(coinType: Long) : this(
        coinType,
        0,
        Chain.External,
        0,
    )

    /**
     * Build path
     * @return Path according BIP32
     */
    fun buildPath(): DerivationPath {
        val nodes = listOf(
            DerivationNode.Hardened(purpose),
            DerivationNode.Hardened(coinType),
            DerivationNode.Hardened(account),
            DerivationNode.NonHardened(change.index),
            DerivationNode.NonHardened(addressIndex),
        )
        return DerivationPath(nodes)
    }

    companion object {
        const val purpose: Long = 44
    }

    enum class Chain(val index: Long) {
        External(0),
        Internal(1),
        ;
    }
}
