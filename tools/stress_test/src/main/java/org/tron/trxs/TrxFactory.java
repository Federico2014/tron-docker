package org.tron.trxs;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j(topic = "trxFactory")
public class TrxFactory {

  private static final TrxFactory INSTANCE = new TrxFactory();

  @Setter
  @Getter
  private long validPeriod = 24 * 60 * 60 * 1000L;

  @Setter
  @Getter
  private AtomicLong time = new AtomicLong(System.currentTimeMillis());

  @Setter
  @Getter
  private ByteString refBlockNum;

  @Setter
  @Getter
  private ByteString refBlockHash;

  @Setter
  @Getter
  private KeyPair keyPair;

  @Setter
  @Getter
  private TrxConfig config;

  private ScheduledExecutorService updateExecutor = Executors
      .newSingleThreadScheduledExecutor();

  private WalletGrpc.WalletBlockingStub blockingStubFull = null;

  private String methodSign = "transfer(address,uint256)";
  private String contractData;

  public static void initInstance() {
    INSTANCE.config = TrxConfig.getInstance();
    INSTANCE.keyPair = new KeyPair(INSTANCE.config.getPrivateKey());

    long expirationTime = System.currentTimeMillis() + INSTANCE.validPeriod;
    INSTANCE.time.set(expirationTime);
    byte[] refBlockNumber = ByteArray.fromLong(TrxConfig.getInstance().getRefBlockNum());
    INSTANCE.refBlockNum = ByteString.copyFrom(ByteArray.subArray(refBlockNumber, 6, 8));
    byte[] refBlockHash = ByteArray.fromHexString(TrxConfig.getInstance().getRefBlockHash());
    INSTANCE.refBlockHash = ByteString.copyFrom(ByteArray.subArray(refBlockHash, 8, 16));

    ManagedChannel channelFull = ManagedChannelBuilder.forTarget(INSTANCE.config.getUpdateRefUrl())
        .usePlaintext()
        .build();
    INSTANCE.blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    INSTANCE.contractData = createContractData(INSTANCE.methodSign, INSTANCE.config.getToAddress(),
        INSTANCE.config.getTransferTrc20Amount());
  }

  public void updateTrxReference() {
    if (config.isUpdateRef()) {
      update();
      updateExecutor.scheduleWithFixedDelay(() -> {
        try {
          update();
        } catch (Exception e) {
          log.error("failed to update the transaction reference");
          e.printStackTrace();
          System.exit(1);
        }
      }, 0, 60, TimeUnit.SECONDS);
    }
  }

  private void update() {
    log.info("begin to update the transaction reference");
    time.set(Math.max(System.currentTimeMillis() + validPeriod, time.get()));
    Block block = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
    long blockNum = block.getBlockHeader().getRawData().getNumber() - 1;
    byte[] blockHash = block.getBlockHeader().getRawData().getParentHash().toByteArray();
    refBlockHash = ByteString.copyFrom(ByteArray.subArray(blockHash, 8, 16));
    refBlockNum = ByteString.copyFrom(ByteArray.subArray(ByteArray.fromLong(blockNum), 6, 8));
  }

  public void close() {
    updateExecutor.shutdown();
  }

  public Transaction createTransferTrx() {
    Contract.TransferContract contract = Contract.TransferContract.newBuilder()
        .setOwnerAddress(ByteString.fromHex(config.getFromAddress()))
        .setToAddress(ByteString.fromHex(config.getToAddress()))
        .setAmount(config.getTransferAmount())
        .build();

    Transaction transaction = createTransaction(contract, ContractType.TransferContract);
    transaction = sign(transaction);
    return transaction;
  }

  public Transaction createTransferTrc10() {
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

  public Transaction createTransferTrc20() {

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
    System.arraycopy(ByteArray.fromLong(amount), 0, params, 56, 8);
    return Hex.toHexString(selector) + Hex.toHexString(params);
  }


  public Transaction createTransaction(com.google.protobuf.Message message,
      ContractType contractType) {
    Transaction.raw.Builder transactionBuilder = Transaction.raw.newBuilder().addContract(
        Transaction.Contract.newBuilder().setType(contractType).setParameter(
            Any.pack(message)).build());

    Transaction transaction = Transaction.newBuilder().setRawData(transactionBuilder.build())
        .build();
    time.incrementAndGet();
    return setReferenceAndExpiration(transaction);
  }

  private Transaction setReference(Transaction transaction, long blockNum,
      byte[] blockHash) {
    byte[] refBlockNum = ByteArray.fromLong(blockNum);
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setRefBlockHash(ByteString.copyFrom(blockHash))
        .setRefBlockBytes(ByteString.copyFrom(refBlockNum))
        .build();
    return transaction.toBuilder().setRawData(rawData).build();
  }

  private Transaction setReferenceAndExpiration(Transaction transaction) {
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setExpiration(time.get())
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

  public Transaction sign(Transaction transaction) {
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

  public static TrxFactory getInstance() {
    return INSTANCE;
  }
}
