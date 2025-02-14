package org.tron;

import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

@Command(name = "broadcast",
    description = "Broadcast the transactions and compute the TPS.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred,please check logs/stress_test.log"})
public class BroadcastTxs implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    return null;
  }
}
