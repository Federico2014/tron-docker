package org.tron;

import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

@Slf4j
@Command(name = "generate",
    description = "Generate the transactions and store them for stress test.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/stress_test.log"})
public class GenerateTxs implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    return null;
  }
}
