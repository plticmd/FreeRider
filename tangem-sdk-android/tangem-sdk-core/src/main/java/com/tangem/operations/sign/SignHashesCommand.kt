package com.tangem.operations.sign

import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.CompletionCallback
import com.tangem.crypto.hdWallet.DerivationPath

/**
 * Signs transaction hash using a wallet private key, stored on the card.
 * @property hashes Array of transaction hashes.
 * @property walletPublicKey: Public key of the wallet, using for sign.
 * @property derivationPath: Derivation path of the wallet. Optional. COS v. 4.28 and higher,
 */
class SignHashesCommand(
    private val hashes: Array<ByteArray>,
    private val walletPublicKey: ByteArray,
    private val derivationPath: DerivationPath? = null,
) : CardSessionRunnable<SignHashesResponse> {

    override fun run(session: CardSession, callback: CompletionCallback<SignHashesResponse>) {
        SignCommand(hashes, walletPublicKey, derivationPath).run(session, callback)
    }
}

typealias SignHashesResponse = SignResponse
