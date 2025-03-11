package org.tron;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.tron.trident.core.exceptions.IllegalException;
import org.tron.trxs.ReplayTrxGenerator;
import org.tron.trxs.TrxConfig;
import org.tron.trxs.TrxFactory;
import org.tron.trxs.TrxGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "generate")
@Command(name = "generate",
    description = "Generate the transactions and store them for stress test.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred, please check logs/stress_test.log"})
public class GenerateTrx implements Callable<Integer> {

  @CommandLine.Spec
  public static CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for generating transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  @Override
  public Integer call() throws IllegalException {
    if (help) {
      spec.commandLine().usage(System.out);
      return 0;
    }

    Config stressConfig = ConfigFactory.load();
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      stressConfig = ConfigFactory.parseFile(Paths.get(config).toFile());
    } else {
      logger.error("stress test config file {} not exists!", config);
      System.exit(-1);
    }
    TrxConfig.initParams(stressConfig);
    TrxConfig config = TrxConfig.getInstance();
    logger.info("load the config file successfully!");

    if (config.isGenerateTrx()) {
      int dispatchCount = config.getTotalTrxCnt() / config.getSingleTaskCnt();
      int lastTaskCnt = config.getSingleTaskCnt();
      if (config.getTotalTrxCnt() % config.getSingleTaskCnt() != 0) {
        dispatchCount = dispatchCount + 1;
        lastTaskCnt = config.getTotalTrxCnt() % config.getSingleTaskCnt();
      }
      logger.info("start to generate the transactions");
      TrxFactory.initInstance();
      for (int i = 0; i < dispatchCount; i++) {
        new TrxGenerator(
            i == (dispatchCount - 1) ? lastTaskCnt
                : config.getSingleTaskCnt(), i).setTotalTask(dispatchCount).start();
      }

      logger.info("finish generating the transactions");
    }

    if (config.isRelay()) {
      logger.info("start to relay the transactions");
      new ReplayTrxGenerator().start();
      logger.info("finish relaying the transactions");
    }

    return 0;
  }
}
