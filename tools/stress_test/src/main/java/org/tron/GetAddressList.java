package org.tron;

import static org.tron.plugins.utils.Constant.ACCOUNT_STORE;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.tron.plugins.utils.db.DBInterface;
import org.tron.plugins.utils.db.DBIterator;
import org.tron.plugins.utils.db.DbTool;
import org.tron.protos.contract.BalanceContract.TransferContract;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.proto.Chain.Transaction.Contract.ContractType;
import org.tron.trident.proto.Contract.TransferAssetContract;
import org.tron.trident.proto.Contract.TriggerSmartContract;
import org.tron.trident.proto.Response.BlockExtention;
import org.tron.trident.proto.Response.TransactionExtention;
import org.tron.trident.utils.Base58Check;
import org.tron.trxs.TxConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j(topic = "getAddressList")
@Command(name = "getAddressList",
    description = "Collect the address list from the account database.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred, please check logs/stress_test.log"})
public class GetAddressList implements Callable<Integer> {

  @CommandLine.Spec
  public static CommandLine.Model.CommandSpec spec;

  @Option(names = {"-c", "--config"},
      defaultValue = "stress.conf",
      description = "configure the parameters for broadcasting transactions."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

//  @CommandLine.Option(names = {"-d", "--database-directory"},
//      defaultValue = "output-directory",
//      description = "java-tron database directory path. Default: ${DEFAULT-VALUE}")
//  private String database;

  @Option(names = {"-h", "--help"})
  private boolean help;


  @Option(names = {"-o", "--output"},
      defaultValue = "address-list.csv",
      description = "store the collected address list."
          + " Default: ${DEFAULT-VALUE}")
  private String output;

  private ApiWrapper apiWrapper;

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
      logger.error("Stress test config file [" + config + "] not exists!");
      spec.commandLine().getErr()
          .format("Stress test config file [%s] not exists!", config)
          .println();
      System.exit(1);
    }
    TxConfig.initParams(stressConfig);
    TxConfig config = TxConfig.getInstance();

//    if (config.getGetAddressStartNumber() < 0 || config.getGetAddressStartNumber() >= config
//        .getGetAddressEndNumber()) {
//      logger.error("invalid start Block: {}, end Block: {}", config.getGetAddressStartNumber(),
//          config.getGetAddressEndNumber());
//      spec.commandLine().getErr()
//          .format("invalid start Block: %d, end Block: %d", config.getGetAddressStartNumber(),
//              config.getGetAddressEndNumber())
//          .println();
//      System.exit(1);
//    }

    if (config.getGetAddressTotalNumber() <= 0) {
      logger.error("invalid target number: {}", config.getGetAddressTotalNumber());
      spec.commandLine().getErr()
          .format("invalid target number: %d", config.getGetAddressTotalNumber())
          .println();
      System.exit(1);
    }

//    if (config.getGetAddressUrl().isEmpty()) {
//      logger.error("no available get address url found.");
//      spec.commandLine().getErr().println("no available get address url found.");
//      System.exit(1);
//    }
//
//    apiWrapper = new ApiWrapper(config.getGetAddressUrl(), config.getGetAddressUrl(),
//        config.getPrivateKey());

//    Set<ByteString> addressList = getAddressList(config.getGetAddressStartNumber(),
//        config.getGetAddressEndNumber(),
//        config.getGetAddressTotalNumber());
    Set<ByteString> addressList = getAddressListFromDB(config.getGetAddressDbPath(),
        config.getGetAddressTotalNumber());
    writeToFile(addressList, output);
    return 0;
  }

  private Set<ByteString> getAddressListFromDB(String dbPath, int totalNumber)
      throws IOException, RocksDBException {
    Set<ByteString> addressList = new HashSet<>();
    String srcDir = dbPath + File.separator + "database";
    DBInterface accountStore = DbTool.getDB(srcDir, ACCOUNT_STORE);
    DBIterator iterator = accountStore.iterator();
    for (iterator.seekToFirst(); iterator.valid(); iterator.next()) {
      addressList.add(ByteString.copyFrom(iterator.getKey()));
      if (addressList.size() % 10000 == 0) {
        logger.info("collecting address list, current size: {}, target: {}", addressList.size(),
            totalNumber);
        spec.commandLine().getOut()
            .format("collecting address list, current size: %d, target: %d", addressList.size(),
                totalNumber)
            .println();
      }
      if (addressList.size() >= totalNumber) {
        logger
            .info("finishing collecting address list: {}, target: {}", addressList.size(),
                totalNumber);
        spec.commandLine().getOut()
            .format("finishing collecting address list: %d, target: %d",
                addressList.size(), totalNumber)
            .println();
        return addressList;
      }
    }

    logger
        .info("finishing collecting address list: {}, target: {}", addressList.size(),
            totalNumber);
    spec.commandLine().getOut()
        .format("finishing collecting address list: %d, target: %d",
            addressList.size(), totalNumber).println();

    DbTool.close();
    return addressList;
  }

  private Set<ByteString> getAddressList(long startBlockNumber, long endBlockNumber,
      int totalNumber) throws InterruptedException {
    if (startBlockNumber < endBlockNumber) {
      logger.info("startNumber: {}, endNumber: {}", startBlockNumber, endBlockNumber);
    } else {
      logger.error("invalid startNumber: {}, endNumber: {}", startBlockNumber, endBlockNumber);
      throw new IllegalArgumentException();
    }

    Set<ByteString> addressList = new HashSet<>();
    BlockExtention block;
    for (long i = startBlockNumber; i < endBlockNumber; i++) {
      if (i % 100 == 0) {
        logger.info("current block number: {}", i);
      }

      try {
        block = apiWrapper.getBlockByNum(i);
      } catch (Exception e) {
        Thread.sleep(500);
        continue;
      }

      if (block.getTransactionsCount() <= 0) {
        continue;
      }

      for (TransactionExtention tx : block.getTransactionsList()) {
        int cnt = 0;
        ContractType type = tx.getTransaction().getRawData().getContract(0).getType();
        try {
          switch (type) {
            case TransferContract:
              TransferContract transferContract = tx.getTransaction().getRawData()
                  .getContract(0)
                  .getParameter().unpack(TransferContract.class);
              if (addressList.add(transferContract.getOwnerAddress())) {
                cnt++;
              }
              if (addressList.add(transferContract.getToAddress())) {
                cnt++;
              }
              break;
            case TransferAssetContract:
              TransferAssetContract transferAssetContract = tx.getTransaction().getRawData()
                  .getContract(0)
                  .getParameter().unpack(TransferAssetContract.class);
              if (addressList.add(transferAssetContract.getOwnerAddress())) {
                cnt++;
              }
              if (addressList.add(transferAssetContract.getToAddress())) {
                cnt++;
              }
              break;
            case TriggerSmartContract:
              TriggerSmartContract triggerSmartContract = tx.getTransaction().getRawData()
                  .getContract(0)
                  .getParameter().unpack(TriggerSmartContract.class);
              if (addressList.add(triggerSmartContract.getOwnerAddress())) {
                cnt++;
              }
              break;
            default:
          }
        } catch (InvalidProtocolBufferException e) {
          e.printStackTrace();
        }

        if (cnt > 0 && addressList.size() % 10000 == 0) {
          logger.info("collecting address list, current size: {}, target: {}", addressList.size(),
              totalNumber);
          spec.commandLine().getOut()
              .format("collecting address list, current size: %d, target: %d", addressList.size(),
                  totalNumber)
              .println();
        }

        if (addressList.size() > totalNumber) {
          logger
              .info("finishing collecting address list: {}, target: {}", addressList.size(),
                  totalNumber);
          spec.commandLine().getOut()
              .format("finishing collecting address list: %d, target: %d",
                  addressList.size(), totalNumber)
              .println();
          return addressList;
        }
      }
    }

    logger
        .info("finishing collecting address list: {}, target: {}", addressList.size(),
            totalNumber);
    spec.commandLine().getOut()
        .format("finishing collecting address list: %d, target: %d",
            addressList.size(), totalNumber).println();
    return addressList;
  }

  public static void writeToFile(Set<ByteString> data, String filePath) {
    try (BufferedWriter writer = new BufferedWriter(
        new FileWriter(filePath))) {
      for (ByteString value : data) {
        String address = Base58Check.bytesToBase58(value.toByteArray());
        writer.write(address);
        writer.newLine();
      }
      writer.flush();
      logger.info("write to file success: " + filePath);
      spec.commandLine().getOut().println("write to file success: " + filePath);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
