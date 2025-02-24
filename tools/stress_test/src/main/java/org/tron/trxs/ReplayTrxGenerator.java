package org.tron.trxs;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.api.GrpcAPI;
import org.tron.trident.api.GrpcAPI.EmptyMessage;
import org.tron.trident.api.WalletGrpc;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trident.proto.Chain.Block;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Response.BlockList;

@Slf4j(topic = "relayTrxGenerator")
public class ReplayTrxGenerator {

  private long startNum;
  private long endNum;
  private String outputFile;
  private String outputDir = "stress-test-output";

  private ManagedChannel channelFull;
  private WalletGrpc.WalletBlockingStub blockingStubFull;
  public static List<Transaction> transactionsOfReplay = new ArrayList<>();
  public static AtomicInteger indexOfReplayTransaction = new AtomicInteger();
  private int count = 0;

  private volatile boolean isReplayGenerate = true;
  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();

  FileOutputStream fos = null;
  CountDownLatch countDownLatch = null;
  private ExecutorService savePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "save-trx"));

  private ExecutorService generatePool = Executors.newFixedThreadPool(2,
      r -> new Thread(r, "relay-trx"));

  public ReplayTrxGenerator(String outputFile) throws IllegalException {
    File dir = new File(outputDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }

    this.outputFile = outputDir + File.separator + outputFile;
    this.startNum = TrxConfig.getInstance().getRelayStartNumber();
    this.endNum = TrxConfig.getInstance().getRelayEndNumber();

    System.out.println("relay url: " + TrxConfig.getInstance().getRelayUrl());
    channelFull = ManagedChannelBuilder
        .forTarget(TrxConfig.getInstance().getRelayUrl())
        .usePlaintext()
        .build();
    this.blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
//    Block block = blockingStubFull.getNowBlock(EmptyMessage.newBuilder().build());
//    System.out.println("blockNumber: " + block.getBlockHeader().getRawData().getNumber());

    ApiWrapper apiWrapper2 = ApiWrapper.ofMainnet(TrxConfig.getInstance().getPrivateKey());
    Block block3 = apiWrapper2.getNowBlock();
    System.out.println("blockNumber2: " + block3.getBlockHeader().getRawData().getNumber());

    ApiWrapper apiWrapper = new ApiWrapper(TrxConfig.getInstance().getRelayUrl(),
        TrxConfig.getInstance().getRelayUrl(),
        TrxConfig.getInstance().getPrivateKey());
    Block block2 = apiWrapper.getNowBlock();
    System.out.println("blockNumber2: " + block2.getBlockHeader().getRawData().getNumber());
  }

  public ReplayTrxGenerator() throws IllegalException {
    this("relay-trx.csv");
  }

  private void consumerGenerateTransaction() throws IOException {
    if (transactions.isEmpty()) {
      try {
        Thread.sleep(100);
        return;
      } catch (InterruptedException e) {
        System.out.println(e);
      }
    }

    Transaction transaction = transactions.poll();
    transaction.writeDelimitedTo(fos);

    long count = countDownLatch.getCount();
    if (count % 100 == 0) {
      fos.flush();
      log.info("relay trx task, remain: %d, pending size: %d",
          countDownLatch.getCount(), transactions.size());
    }

    countDownLatch.countDown();
  }

  public void start() {
    GrpcAPI.BlockLimit.Builder builder = GrpcAPI.BlockLimit.newBuilder();
    BlockList blockList;
    Optional<BlockList> result;

    int step = 5;
    log.info(
        String.format("extract the transaction from block: %s to block: %s.", startNum, endNum));
    for (int i = (int) startNum; i < endNum; i = i + step) {
      builder.setStartNum(i);
      builder.setEndNum(i + step);
      blockList = blockingStubFull.getBlockByLimitNext(builder.build());
      result = Optional.ofNullable(blockList);
      if (result.isPresent()) {
        blockList = result.get();
        if (blockList.getBlockCount() > 0) {
          for (Block block : blockList.getBlockList()) {
            if (block.getTransactionsCount() > 0) {
              transactionsOfReplay.addAll(block.getTransactionsList());
            }
          }
        }
      }
      log.info(String
          .format("already extracted the transaction from block: %d to block: %d.", i, i + step));
    }

    log.info("total relay transactions cnt: " + transactionsOfReplay.size());
    this.count = transactionsOfReplay.size();

    savePool.submit(() -> {
      while (isReplayGenerate) {
        try {
          consumerGenerateTransaction();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });

    try {
      fos = new FileOutputStream(this.outputFile);
      countDownLatch = new CountDownLatch(this.count);

      while (indexOfReplayTransaction.get() < transactionsOfReplay.size()) {
        transactions.add(transactionsOfReplay.get(indexOfReplayTransaction.get()));
        indexOfReplayTransaction.incrementAndGet();
      }

      countDownLatch.await();
      isReplayGenerate = false;

      fos.flush();
      fos.close();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    } finally {
      TrxGenerator.shutDown(generatePool, savePool);
    }
  }
}
