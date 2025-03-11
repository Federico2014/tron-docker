package org.tron.trxs;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.core.key.KeyPair;
import org.tron.trident.core.transaction.BlockId;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.core.utils.Sha256Hash;
import org.tron.trident.crypto.Hash;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Chain.Block;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Chain.Transaction.Contract.ContractType;
import org.tron.trident.proto.Contract;
import org.tron.trident.proto.Response.TransactionExtention;

@Slf4j(topic = "trxFactory")
public class TrxFactory {

  private static final TrxFactory INSTANCE = new TrxFactory();

  private static final long feeLimit = 2000000000L;

  @Setter
  @Getter
  private long validPeriod = 24 * 60 * 60 * 1000L;

  @Setter
  @Getter
  private AtomicLong time = new AtomicLong(System.currentTimeMillis() + validPeriod);

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

  @Getter
  private ApiWrapper apiWrapper;

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

    INSTANCE.apiWrapper = new ApiWrapper(INSTANCE.config.getUpdateRefUrl(),
        INSTANCE.config.getUpdateRefUrl(), INSTANCE.config.getPrivateKey());
    BlockId blockId = new BlockId(refBlockHash, TrxConfig.getInstance().getRefBlockNum());
    INSTANCE.apiWrapper.enableLocalCreate(blockId, expirationTime);

    INSTANCE.contractData = createContractData(INSTANCE.methodSign, INSTANCE.config.getToAddress(),
        INSTANCE.config.getTransferTrc20Amount());
    if (INSTANCE.config.isUpdateRef()) {
      INSTANCE.update();
      INSTANCE.updateTrxReference();
    }
  }

  public void updateTrxReference() {
    if (config.isUpdateRef()) {
      updateExecutor.scheduleWithFixedDelay(() -> {
        try {
          update();
        } catch (Exception e) {
          logger.error("failed to update the transaction reference");
          e.printStackTrace();
          System.exit(1);
        }
      }, 60, 60, TimeUnit.SECONDS);
    }
  }

  private void update() {
    logger.info("begin to update the transaction reference");
    time.set(Math.max(System.currentTimeMillis() + validPeriod, time.get()));
    Block block = null;
    try {
      block = apiWrapper.getNowBlock();
    } catch (IllegalException e) {
      logger.error("failed to get the block");
      e.printStackTrace();
      System.exit(1);
    }
    long blockNum = block.getBlockHeader().getRawData().getNumber() - 1;
    byte[] blockHash = block.getBlockHeader().getRawData().getParentHash().toByteArray();
    refBlockHash = ByteString.copyFrom(ByteArray.subArray(blockHash, 8, 16));
    refBlockNum = ByteString.copyFrom(ByteArray.subArray(ByteArray.fromLong(blockNum), 6, 8));

    BlockId blockId = new BlockId(blockHash, blockNum);
    long expiration = time.incrementAndGet();
    INSTANCE.apiWrapper.setReferHeadBlockId(blockId);
    INSTANCE.apiWrapper.setExpireTimeStamp(expiration);
    logger.info("finish updating the transaction reference");
  }

  public void close() {
    updateExecutor.shutdown();
  }

  public Transaction getTransferTrx() throws IllegalException {
    TransactionExtention transaction = apiWrapper
        .transfer(config.getFromAddress(), config.getToAddress(), config.getTransferAmount());
    return apiWrapper.signTransaction(transaction);
  }

  public Transaction getTransferTrc10() throws IllegalException {
    TransactionExtention transaction = apiWrapper
        .transferTrc10(config.getFromAddress(), config.getToAddress(), config.getTrc10Id(),
            config.getTransferTrc10Amount());
    return apiWrapper.signTransaction(transaction);
  }

  public Transaction getTransferTrc20() throws Exception {
    TransactionExtention transaction = apiWrapper
        .triggerContract(config.getFromAddress(), config.getTrc20ContractAddress(), contractData,
            0L, 0L, null, feeLimit);
    return apiWrapper.signTransaction(transaction);
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
        .setAssetName(ByteString.copyFromUtf8(String.valueOf(config.getTrc10Id())))
        .setOwnerAddress(ByteString.fromHex(config.getFromAddress()))
        .setToAddress(ByteString.fromHex(config.getToAddress()))
        .setAmount(config.getTransferTrc10Amount())
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
    Transaction.raw raw = transaction.getRawData().toBuilder().setFeeLimit(feeLimit).build();
    transaction = sign(transaction.toBuilder().setRawData(raw).build());
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
    long expiration = time.incrementAndGet();
    return setReferenceAndExpiration(transaction, expiration);
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

  private Transaction setReferenceAndExpiration(Transaction transaction, long expiration) {
    Transaction.raw rawData = transaction.getRawData().toBuilder()
        .setExpiration(expiration)
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
