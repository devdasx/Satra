package dev.satra.wallet.data.send.ton;

import org.ton.bigint.BigInt;
import org.ton.block.CurrencyCollection;
import org.ton.block.Message;
import org.ton.block.MsgAddress;
import org.ton.cell.Cell;
import org.ton.contract.wallet.MessageData;
import org.ton.contract.wallet.WalletTransfer;

import java.math.BigInteger;

final class TonSdkInterop {
    private TonSdkInterop() {
    }

    static BigInt bigInt(BigInteger value) {
        return new BigInt(value);
    }

    static WalletTransfer walletTransfer(
            MsgAddress destination,
            boolean bounceable,
            CurrencyCollection coins,
            int sendMode,
            MessageData messageData
    ) {
        return new WalletTransfer(destination, bounceable, coins, sendMode, messageData);
    }

    static Cell messageCell(Message<Cell> message) {
        return Message.Companion.getAny().createCell(message);
    }
}
