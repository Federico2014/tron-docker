package org.tron.trxs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.GenerateTrx;
import org.tron.trident.crypto.Hash;
import org.tron.trident.proto.Chain.Transaction;

@Slf4j(topic = "trxGenerator")
public class TrxGenerator {

  private int count;
  private String outputFile;
  private String outputDir = "stress-test-output";

  private int totalTask;
  private int index;

  private volatile boolean isGenerate = true;
  private final ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();
  FileOutputStream fos = null;
  CountDownLatch countDownLatch = null;
  private final Random random = new Random(System.currentTimeMillis());

  private final TrxConfig config = TrxConfig.getInstance();

  private final ExecutorService savePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "save-trx"));
  private final ExecutorService generatePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "generate-trx"));

  public TrxGenerator(String outputFile, int count, int index) {
    File dir = new File(outputDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }

    this.outputFile = outputDir + File.separator + outputFile;
    this.count = count;
    this.index = index;
  }

  public TrxGenerator(int count) {
    this("generate-trx.csv", count, 0);
  }

  public TrxGenerator(int count, int index) {
    this("generate-trx" + index + ".csv", count, index);
  }

  public TrxGenerator setTotalTask(int totalTask) {
    this.totalTask = totalTask;
    return this;
  }

  private Transaction generateTransaction() throws Exception {
    int randomInt = random.nextInt(100);
    TrxType type = config.findTransactionType(randomInt);
    Transaction transaction;
    long expiration = TrxFactory.getInstance().getTime().incrementAndGet();
    TrxFactory.getInstance().getApiWrapper().setExpireTimeStamp(expiration);
    switch (type) {
      case TRANSFER:
        transaction = TrxFactory.getInstance().getTransferTrx();
        break;
      case TRANSFER_TRC10:
        transaction = TrxFactory.getInstance().getTransferTrc10();
        break;
      case TRANSFER_TRC20:
        transaction = TrxFactory.getInstance().getTransferTrc20();
        break;
      default:
        return null;
    }

    return transaction;
  }

  private void consumerGenerateTransaction() throws IOException {
    if (transactions.isEmpty()) {
      try {
        Thread.sleep(100);
        return;
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Transaction transaction = transactions.poll();
    assert transaction != null;
    transaction.writeDelimitedTo(fos);

    long cnt = countDownLatch.getCount();
    if (cnt % 1000 == 0) {
      fos.flush();
      logger.info(
          String.format("generate trx task: %d/%d, task remain: %d, task pending size: %d",
              index + 1, totalTask, countDownLatch.getCount(), transactions.size()));
    }

    countDownLatch.countDown();
  }

  public void start() {
    savePool.submit(() -> {
      while (isGenerate) {
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

      LongStream.range(0L, this.count).forEach(
          l -> generatePool.execute(
              () -> {
                try {
                  Optional.ofNullable(generateTransaction()).ifPresent(transactions::add);
                } catch (Exception e) {
                  logger.error("failed to generate the transaction.");
                  e.printStackTrace();
                  System.exit(1);
                }
              }));

      countDownLatch.await();
      isGenerate = false;
      fos.flush();
      fos.close();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    } finally {
      shutDown(generatePool, savePool);
    }
  }

  static void shutDown(ExecutorService generatePool, ExecutorService savePool) {
    generatePool.shutdown();
    while (!generatePool.isTerminated()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    savePool.shutdown();
    while (!savePool.isTerminated()) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
