package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trxs.TransactionGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.tron.utils.Constant.*;

@Slf4j
@Command(name = "generate",
    description = "Generate the transactions and store them for stress test.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/stress_test.log"})
public class GenerateTxs implements Callable<Integer> {

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for generating transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  private static Integer singleTaskTransactionCount = 800000;
  private static Integer dispatchCount;

  @Override
  public Integer call() throws Exception {
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

    int totalTxsCnt = 0;
    if (stressConfig.hasPath(TOTAL_TXS_CNT) && stressConfig.getInt(TOTAL_TXS_CNT) > 0) {
      totalTxsCnt = stressConfig.getInt(TOTAL_TXS_CNT);
    } else {
      log.error("totalTxsCnt parameter is not valid!");
      spec.commandLine().getErr().format("totalTxsCnt parameter is not valid!").println();
      System.exit(-1);
    }

    dispatchCount = totalTxsCnt/singleTaskTransactionCount;
    log.info("start generate the transactions");
    spec.commandLine().getOut().println("start generate the transactions");

    TransactionGenerator.calculateRanges(stressConfig.getConfig(TRX_TYPE));

    return 0;
  }
}
