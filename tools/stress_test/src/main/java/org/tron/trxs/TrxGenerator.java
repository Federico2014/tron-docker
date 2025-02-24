package org.tron.trxs;

import java.io.File;
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
import org.tron.GenerateTrx;
import org.tron.trident.proto.Chain.Transaction;

@Slf4j(topic = "trxGenerator")
public class TrxGenerator {

  private int count;
  private String outputFile;
  private String outputDir = "stress-test-output";

  private int totalTask;
  private int index;

  private volatile boolean isGenerate = true;
  private ConcurrentLinkedQueue<Transaction> transactions = new ConcurrentLinkedQueue<>();
  FileOutputStream fos = null;
  CountDownLatch countDownLatch = null;
  private Random random = new Random(System.currentTimeMillis());

  private TrxConfig config = TrxConfig.getInstance();

  private ExecutorService savePool = Executors.newFixedThreadPool(1,
      r -> new Thread(r, "save-trx"));
  private ExecutorService generatePool = Executors.newFixedThreadPool(5,
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

  private Transaction generateTransaction() {
    int randomInt = random.nextInt(100);
    TrxType type = config.findTransactionType(randomInt);
    GenerateTrx.spec.commandLine().getOut()
        .println("generate transaction type: " + type.toString());
    switch (type) {
      case TRANSFER:
        return TrxFactory.getInstance().createTransferTrx();
      case TRANSFER_TRC10:
        return TrxFactory.getInstance().createTransferTrc10();
      case TRANSFER_TRC20:
        return TrxFactory.getInstance().createTransferTrc20();
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

    Transaction transaction = transactions.poll();
    transaction.writeDelimitedTo(fos);

    long count = countDownLatch.getCount();
    if (count % 100 == 0) {
      fos.flush();
      log.info(
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
              () -> Optional.ofNullable(generateTransaction()).ifPresent(transactions::add)));

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
