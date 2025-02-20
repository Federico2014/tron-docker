package org.tron.trxs;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.proto.Chain.Transaction;

@Slf4j(topic = "transactionGenerator")
public class TransactionGenerator {

  private int count;
  private String outputFile;

  private volatile boolean isGenerate = true;
  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();
  FileOutputStream fos = null;
  CountDownLatch countDownLatch = null;
  private Random random = new Random(System.currentTimeMillis());

  private TransactionConfig config = TransactionConfig.getInstance();

  private ExecutorService savePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "save-transaction"));
  private ExecutorService generatePool = Executors.newFixedThreadPool(2,
      r -> new Thread(r, "generate-transaction"));

  public TransactionGenerator(String outputFile, int count) {
    this.outputFile = outputFile;
    this.count = count;
  }

  public TransactionGenerator(int count) {
    this("gen-transaction.csv", count);
  }

  public TransactionGenerator(int count, int index) {
    this("gen-transaction" + index + ".csv", count);
  }


  private Transaction generateTransaction() {
    int randomInt = random.nextInt(100);
    TransactionType type = config.findTransactionType(randomInt);
    switch (type) {
      case TRANSFER:
        return TransactionFactory.createTransferTrx();
      case TRANSFER_TRC10:
        return TransactionFactory.createTransferTrc10();
      case TRANSFER_TRC20:
        return TransactionFactory.createTransferTrc20();
      default:
        return null;
    }
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

    consumeTransaction(transactions, fos, countDownLatch);
  }

  static void consumeTransaction(ConcurrentLinkedQueue<Transaction> transactions,
      FileOutputStream fos, CountDownLatch countDownLatch) throws IOException {
    Transaction transaction = transactions.poll();
    transaction.writeDelimitedTo(fos);

    long count = countDownLatch.getCount();
    if (count % 10000 == 0) {
      fos.flush();
      System.out.printf("Generate transaction success ------- ------- Remain: %d, Pending size: %d",
          countDownLatch.getCount(), transactions.size());
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

      LongStream.range(0L, this.count).forEach(l -> {
        generatePool.execute(() -> {
          Optional.ofNullable(generateTransaction()).ifPresent(transactions::add);
        });
      });

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
    while (true) {
      if (generatePool.isTerminated()) {
        break;
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    savePool.shutdown();
    while (true) {
      if (savePool.isTerminated()) {
        break;
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
