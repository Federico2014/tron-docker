package org.tron.trxs;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import org.tron.trident.api.WalletGrpc;
import org.tron.trident.proto.Chain.Block;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Response.BlockList;

@Slf4j(topic = "relayTransactionGenerator")
public class ReplayTransactionGenerator {

  private int startNum;
  private int endNum;
  private String outputFile;
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  public static List<Transaction> transactionsOfReplay = new ArrayList<>();
  public static AtomicInteger indexOfReplayTransaction = new AtomicInteger();
  private int count = 0;

  private volatile boolean isReplayGenerate = true;
  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();

  FileOutputStream fos = null;
  CountDownLatch countDownLatch = null;
  private ExecutorService savePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "save-transaction"));

  private ExecutorService generatePool = Executors.newFixedThreadPool(2,
      r -> new Thread(r, "generate-transaction"));

  public ReplayTransactionGenerator(String outputFile) {
    this.outputFile = outputFile;
    this.startNum = TransactionConfig.getInstance().getRelayStartNumber();
    this.endNum = TransactionConfig.getInstance().getRelayEndNumber();
  }

  public ReplayTransactionGenerator() {
    this("relay-transaction.csv");
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

    log.info("transactions size is " + transactions.size());
    TransactionGenerator.consumeTransaction(transactions, fos, countDownLatch);
  }

  public void start() {
    channelFull = ManagedChannelBuilder.forTarget(TransactionConfig.getInstance().getRelayUrl())
        .usePlaintext()
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    System.out.println("Start replay generate transaction");

    GrpcAPI.BlockLimit.Builder builder = GrpcAPI.BlockLimit.newBuilder();
    builder.setStartNum(2);
    builder.setEndNum(4);
    BlockList blockList = blockingStubFull.getBlockByLimitNext(builder.build());
    Optional<BlockList> result = Optional.ofNullable(blockList);

    int step = 50;
    System.out.println(
        String.format("extract the transaction from block: %s to block: %s.", startNum, endNum));
    for (int i = startNum; i < endNum; i = i + step) {
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
      System.out.println(
          String.format("already extracted the transaction from block: %s to block: %s.", i,
              i + step));
    }

    System.out.println("total transactions cnt: " + transactionsOfReplay.size());
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
      TransactionGenerator.shutDown(generatePool, savePool);
    }
  }
}
