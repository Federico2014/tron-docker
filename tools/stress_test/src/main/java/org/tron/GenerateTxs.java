package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trxs.ReplayTransactionGenerator;
import org.tron.trxs.TransactionConfig;
import org.tron.trxs.TransactionGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "generate")
@Command(name = "generate",
    description = "Generate the transactions and store them for stress test.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/stress_test.log"})
public class GenerateTxs implements Callable<Integer> {

  @CommandLine.Spec
  public static CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for generating transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  public static Integer singleTaskTransactionCount = 800000;
  private static Integer dispatchCount;

  @Override
  public Integer call() {
    if (help) {
      spec.commandLine().usage(System.out);
      return 0;
    }

    Config stressConfig = ConfigFactory.load();
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      stressConfig = ConfigFactory.parseFile(Paths.get(config).toFile());
    } else {
      log.error("Stress test config file [" + config + "] not exists!");
      spec.commandLine().getErr().format("Stress test config file [" + config + "] not exists!")
          .println();
      System.exit(-1);
    }
    TransactionConfig.initParams(stressConfig);
    TransactionConfig config = TransactionConfig.getInstance();

    if (config.isGenerate()) {
      dispatchCount = config.getTotalTrxCnt() / singleTaskTransactionCount;
      log.info("start to generate the transactions");
      spec.commandLine().getOut().println("start to generate the transactions");

      for (int i = 0; i <= dispatchCount; i++) {
        new TransactionGenerator(
            i == dispatchCount ? config.getTotalTrxCnt() % singleTaskTransactionCount
                : singleTaskTransactionCount, i).start();
      }

      log.info("finish generating the transactions");
      spec.commandLine().getOut().println("finish generating the transactions");
    }

    if (config.isRelay()) {
      log.info("start to relay the transactions");
      spec.commandLine().getOut().println("start to relay the transactions");

      new ReplayTransactionGenerator().start();

      log.info("finish relaying the transactions");
      spec.commandLine().getOut().println("finish relaying the transactions");
    }

    return 0;
  }
}
