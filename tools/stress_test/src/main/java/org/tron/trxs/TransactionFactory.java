package org.tron.trxs;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.util.encoders.Hex;
import org.tron.trident.api.GrpcAPI.EmptyMessage;
import org.tron.trident.api.WalletGrpc;
import org.tron.trident.core.key.KeyPair;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.core.utils.Sha256Hash;
import org.tron.trident.crypto.Hash;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Chain.Block;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Chain.Transaction.Contract.ContractType;
import org.tron.trident.proto.Contract;

public class TransactionFactory {

  private static final long validPeriod = 24 * 60 * 60 * 1000L;
  private static long time;

  private static ByteString refBlockNum;
  private static ByteString refBlockHash;
  private static KeyPair keyPair;

  private static TransactionConfig config;

  private static ScheduledExecutorService updateExecutor = Executors
      .newSingleThreadScheduledExecutor();

  private static ManagedChannel channelFull = null;
  private static WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private static final String methodSign = "transfer(address,uint256)";
  private static String contractData;

  public static void init() {
    config = TransactionConfig.getInstance();
    keyPair = new KeyPair(config.getPrivateKey());
    time = System.currentTimeMillis() + validPeriod;

    channelFull = ManagedChannelBuilder.forTarget(config.getUrl())
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    contractData = createContractData(methodSign, config.getToAddress(),
        config.getTransferTrc20Amount());

    if (config.produceValidTxs) {
      updateExecutor.scheduleWithFixedDelay(() -> {
        try {
          update();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, 100, 60, TimeUnit.SECONDS);
    }
  }

  private static void update() {
    time = System.currentTimeMillis() + validPeriod;
    Block block = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
    long blockNum = block.getBlockHeader().getRawData().getNumber() - 1;
    byte[] blockHash = block.getBlockHeader().getRawData().getParentHash().toByteArray();
    refBlockHash = ByteString.copyFrom(ByteArray.subArray(blockHash, 8, 16));
    refBlockNum = ByteString.copyFrom(ByteArray.subArray(ByteArray.fromLong(blockNum), 6, 8));
  }

  public void close() {
    updateExecutor.shutdown();
  }

  public static Transaction createTransferTrx() {
    Contract.TransferContract contract = Contract.TransferContract.newBuilder()
        .setOwnerAddress(ByteString.fromHex(config.getFromAddress()))
        .setToAddress(ByteString.fromHex(config.getToAddress()))
        .setAmount(config.getTransferAmount())
        .build();

    Transaction transaction = createTransaction(contract, ContractType.TransferContract);
    transaction = sign(transaction);
    return transaction;
  }

  public static Transaction createTransferTrc10() {
    Contract.TransferAssetContract contract = Contract.TransferAssetContract.newBuilder()
        .setAssetName(ByteString.copyFromUtf8(config.getTrc10Id()))
        .setOwnerAddress(ByteString.fromHex(config.getFromAddress()))
        .setToAddress(ByteString.fromHex(config.getToAddress()))
        .setAmount(config.getTransferAmount())
        .build();

    Transaction transaction = createTransaction(contract, ContractType.TransferAssetContract);
    transaction = sign(transaction);
    return transaction;
  }

  public static Transaction createTransferTrc20() {

    Contract.TriggerSmartContract contract = Contract.TriggerSmartContract.newBuilder()
        .setOwnerAddress(ByteString.fromHex(config.getFromAddress()))
        .setContractAddress(ByteString.fromHex(config.getTrc20ContractAddress()))
        .setData(ByteString.fromHex(contractData))
        .setCallValue(0L)
        .setTokenId(Long.parseLong("0"))
        .setCallTokenValue(0L)
        .build();

    Transaction transaction = createTransaction(contract, ContractType.TriggerSmartContract);
    transaction = sign(transaction);
    return transaction;
  }

  private static String createContractData(String methodSign, String receiver, long amount) {
    byte[] selector = new byte[4];
    System.arraycopy(Hash.sha3(methodSign.getBytes()), 0, selector, 0, 4);

    byte[] params = new byte[64];
    System.arraycopy(Hex.decode(receiver), 1, params, 12, 20);
    System.arraycopy(ByteArray.fromLong(amount), 0, params, 24, 8);
    return Hex.toHexString(selector) + Hex.toHexString(params);
  }


  public static Transaction createTransaction(com.google.protobuf.Message message,
      ContractType contractType) {
    Transaction.raw.Builder transactionBuilder = Transaction.raw.newBuilder().addContract(
        Transaction.Contract.newBuilder().setType(contractType).setParameter(
            Any.pack(message)).build());

    Transaction transaction = Transaction.newBuilder().setRawData(transactionBuilder.build())
        .build();
    time++;
    transaction = setExpiration(transaction, time + 1);

    if (config.produceValidTxs) {
      transaction = setReference(transaction);
    } else {
      transaction = setReference(transaction, time, ByteArray.fromLong(time));
    }

    return transaction;
  }

  private static Transaction setReference(Transaction transaction, long blockNum,
      byte[] blockHash) {
    byte[] refBlockNum = ByteArray.fromLong(blockNum);
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setRefBlockHash(ByteString.copyFrom(blockHash))
        .setRefBlockBytes(ByteString.copyFrom(refBlockNum))
        .build();
    return transaction.toBuilder().setRawData(rawData).build();
  }

  private static Transaction setReference(Transaction transaction) {
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setRefBlockHash(refBlockHash)
        .setRefBlockBytes(refBlockNum)
        .build();
    return transaction.toBuilder().setRawData(rawData).build();
  }

  public static Transaction setExpiration(Transaction transaction, long expiration) {
    Transaction.raw rawData = transaction.getRawData().toBuilder().setExpiration(expiration)
        .build();
    return transaction.toBuilder().setRawData(rawData).build();
  }

  public static Transaction sign(Transaction transaction) {
    Transaction.Builder transactionBuilderSigned = transaction.toBuilder();
    byte[] hash = Sha256Hash.hash(true, transaction.getRawData().toByteArray());
    List<Chain.Transaction.Contract> listContract = transaction.getRawData().getContractList();
    for (int i = 0; i < listContract.size(); i++) {
      byte[] signature = KeyPair.signTransaction(hash, keyPair);
      transactionBuilderSigned.addSignature(ByteString.copyFrom(signature));
    }

    transaction = transactionBuilderSigned.build();
    return transaction;
  }
}
