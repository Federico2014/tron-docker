package org.tron.plugins;

import static org.tron.plugins.utils.Constant.BLOCK_INDEX_STORE;
import static org.tron.plugins.utils.Constant.BLOCK_PRODUCED_INTERVAL;
import static org.tron.plugins.utils.Constant.BLOCK_STORE;
import static org.tron.plugins.utils.Constant.DYNAMIC_PROPERTY_STORE;
import static org.tron.plugins.utils.Constant.LATEST_BLOCK_HEADER_NUMBER;
import static org.tron.plugins.utils.Constant.MAINTENANCE_TIME_INTERVAL;
import static org.tron.plugins.utils.Constant.VOTES_ALL_WITNESSES;
import static org.tron.plugins.utils.Constant.VOTES_STORE;
import static org.tron.plugins.utils.Constant.VOTES_WITNESS_LIST;
import static org.tron.plugins.utils.Constant.WITNESS_STORE;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.VotesCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.plugins.utils.FileUtils;
import org.tron.plugins.utils.JsonFormat;
import org.tron.plugins.utils.db.DBInterface;
import org.tron.plugins.utils.db.DBIterator;
import org.tron.plugins.utils.db.DbTool;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.contract.WitnessContract.VoteWitnessContract;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j(topic = "query")
@Command(name = "query",
    description = "query the votes and reward from the database.",
    exitCodeListHeading = "Exit Codes:%n",
    exitCodeList = {
        "0:Successful",
        "n:Internal error: exception occurred, please check logs/toolkit.log"})
public class DbQuery implements Callable<Integer> {

  @CommandLine.Spec
  CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(names = {"-d", "--database-directory"},
      defaultValue = "output-directory",
      description = "java-tron database directory path. Default: ${DEFAULT-VALUE}")
  private String database;

  @CommandLine.Option(names = {"-c", "--config"},
      defaultValue = "query.conf",
      description = "config the votes and reward options."
          + " Default: ${DEFAULT-VALUE}")
  private String config;

  @CommandLine.Option(names = {"-h", "--help"})
  private boolean help;

  private DBInterface witnessStore;
  private DBInterface votesStore;
  private DBInterface dynamicPropertiesStore;
  private DBInterface blockIndexStore;
  private DBInterface blockStore;

  boolean allWitness = false;
  List<String> witnessList = new ArrayList<>();

  private Set<ByteString> voters = new HashSet<>();
  private Map<ByteString, VoteWitnessTx> votesTx = new HashMap<>();

  private void initStore() throws IOException, RocksDBException {
    String srcDir = database + File.separator + "database";
    witnessStore = DbTool.getDB(srcDir, WITNESS_STORE);
    votesStore = DbTool.getDB(srcDir, VOTES_STORE);
    dynamicPropertiesStore = DbTool.getDB(srcDir, DYNAMIC_PROPERTY_STORE);
    blockIndexStore = DbTool.getDB(srcDir, BLOCK_INDEX_STORE);
    blockStore = DbTool.getDB(srcDir, BLOCK_STORE);
  }


  @Override
  public Integer call() throws Exception {
    if (help) {
      spec.commandLine().usage(System.out);
      return 0;
    }

    Config queryConfig = ConfigFactory.load();
    File file = Paths.get(config).toFile();
    if (file.exists() && file.isFile()) {
      queryConfig = ConfigFactory.parseFile(file);
    } else {
      logger.error("Query config file [" + config + "] not exists!");
      spec.commandLine().getErr().format("Fork config file: %s not exists!", config).println();
      return 1;
    }

    File dbFile = Paths.get(database).toFile();
    if (!dbFile.exists() || !dbFile.isDirectory()) {
      logger.error("Database [" + database + "] not exists!");
      spec.commandLine().getErr().format("Database %s not exists!", database).println();
      return 1;
    }
    File tmp = Paths.get(database, "database", "tmp").toFile();
    if (tmp.exists()) {
      FileUtils.deleteDir(tmp);
    }

    initStore();

    processVotes(queryConfig);

    DbTool.close();
    return 0;
  }

  private void processVotes(Config queryConfig) throws BadItemException {
    if (queryConfig.hasPath(VOTES_ALL_WITNESSES)) {
      allWitness = queryConfig.getBoolean(VOTES_ALL_WITNESSES);
    }
    if (queryConfig.hasPath(VOTES_WITNESS_LIST)) {
      witnessList = queryConfig.getStringList(VOTES_WITNESS_LIST);
    }

    if (!allWitness && witnessList.size() == 0) {
      return;
    }

    Map<ByteString, WitnessCapsule> witnesses = new HashMap<>();
    DBIterator iterator = witnessStore.iterator();
    WitnessCapsule witnessCapsule;
    for (iterator.seekToFirst(); iterator.valid(); iterator.next()) {
      witnessCapsule = new WitnessCapsule(iterator.getValue());
      witnesses.put(ByteString.copyFrom(iterator.getKey()), witnessCapsule);
    }

    Map<ByteString, Long> countWitness = countVote();
    loadVotesTx();

    AtomicInteger cnt = new AtomicInteger();
    votesTx.forEach((address, voteWitnessTx) -> {
      if (allWitness || voteWitnessTx.existInWitnessList(witnessList)) {
        cnt.getAndIncrement();
      }
    });
    spec.commandLine().getOut()
        .format("There are  %d new related votes in this epoch", cnt.get())
        .println();
    logger.info("There are {} new related votes in this epoch", cnt.get());
    cnt.set(-1);
    votesTx.forEach((address, voteWitnessTx) -> {
      if (!allWitness && !voteWitnessTx.existInWitnessList(witnessList)) {
        return;
      }
      cnt.getAndIncrement();
      String txHashStr = voteWitnessTx.txHash.toString();
      spec.commandLine().getOut().format("tx %d: %s", cnt.get(), txHashStr).println();
      logger.info("tx {}: {}", cnt.get(), txHashStr);
      String voteWitnessStr = JsonFormat.printToString(voteWitnessTx.voteWitnessContract, true);
      spec.commandLine().getOut().println(voteWitnessStr);
      logger.info(voteWitnessStr);
    });

    countWitness.forEach((address, voteCount) -> {
      WitnessCapsule witness = witnesses.get(address);
      if (witness == null) {
        return;
      }
      witness.setVoteCount(witness.getVoteCount() + voteCount);
      witnesses.put(address, witness);
    });

    spec.commandLine().getOut().println("Display the witness list with latest vote count: ");
    logger.info("Display the witness list with latest vote count: ");
    List<WitnessCapsule> witnessCapsuleList;
    if (allWitness) {
      witnessCapsuleList = new ArrayList<>(witnesses.values());
      Collections.sort(witnessCapsuleList,
          Comparator.comparingLong(WitnessCapsule::getVoteCount).reversed());
    } else {
      List<WitnessCapsule> finalWitnessCapsuleList = new ArrayList<>();
      witnessList.forEach(
          witness -> {
            ByteString address = ByteString.copyFrom(Commons.decodeFromBase58Check(witness));
            WitnessCapsule witnessTmp = witnesses.get(address);
            if (witnessTmp == null) {
              spec.commandLine().getErr().format("address: %s is not witness", witness).println();
              logger.error("address: {} is not witness", witness);
              return;
            }
            finalWitnessCapsuleList.add(witnessTmp);
          }
      );
      witnessCapsuleList = finalWitnessCapsuleList;
    }

//      WitnessList.Builder builder = WitnessList.newBuilder();
//      witnessCapsuleList.forEach(witness -> builder.addWitnesses(witness.getInstance()));
//      String witnessesJson = JsonFormat.printToString(builder.build(), true);
//      spec.commandLine().getOut().println(witnessesJson);
//      logger.info(witnessesJson);

    witnessCapsuleList.forEach(
        witness -> {
          String witnessStr = JsonFormat.printToString(witness.getInstance(), true);
          spec.commandLine().getOut().println(witnessStr);
          logger.info(witnessStr);
        });

  }


  private Map<ByteString, Long> countVote() {
    final Map<ByteString, Long> countWitness = Maps.newHashMap();
    DBIterator dbIterator = votesStore.iterator();
//    long sizeCount = 0;
    dbIterator.seekToFirst();
    while (dbIterator.hasNext()) {
      Entry<byte[], byte[]> next = dbIterator.next();
      voters.add(ByteString.copyFrom(next.getKey()));

      VotesCapsule votes = new VotesCapsule(next.getValue());
      votes.getOldVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) - voteCount);
        } else {
          countWitness.put(voteAddress, -voteCount);
        }
      });
      votes.getNewVotes().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) + voteCount);
        } else {
          countWitness.put(voteAddress, voteCount);
        }
      });
//      sizeCount++;
    }
//    spec.commandLine().getOut().format("There are total %d new votes in this epoch", sizeCount)
//        .println();
//    logger.info("There are total {} new votes in this epoch", sizeCount);
    return countWitness;
  }

  private void loadVotesTx() throws BadItemException {
    long maintenanceTimeInterval = ByteArray
        .toLong(dynamicPropertiesStore.get(MAINTENANCE_TIME_INTERVAL));
    long maintenanceBlockCnt = maintenanceTimeInterval / BLOCK_PRODUCED_INTERVAL;
    long latestBlockNumber = ByteArray
        .toLong(dynamicPropertiesStore.get(LATEST_BLOCK_HEADER_NUMBER));
    long startBlock =
        latestBlockNumber > maintenanceBlockCnt ? latestBlockNumber - maintenanceBlockCnt : 0;
    long block;
    for (block = startBlock; block <= latestBlockNumber; block++) {
      byte[] blockHash = blockIndexStore.get(ByteArray.fromLong(block));
      BlockCapsule blockCapsule = new BlockCapsule(blockStore.get(blockHash));

      blockCapsule.getTransactions().forEach(txCapsule -> {
        ContractType txType = txCapsule.getInstance().getRawData().getContract(0).getType();
        if (!txType.equals(ContractType.VoteWitnessContract)) {
          return;
        }
        try {
          VoteWitnessContract voteWitnessContract = txCapsule.getInstance().getRawData()
              .getContract(0)
              .getParameter().unpack(VoteWitnessContract.class);
          ByteString ownerAddress = voteWitnessContract.getOwnerAddress();
          voteWitnessContract.getVotesList().forEach(vote -> {
            if (voters.contains(ownerAddress)) {
              votesTx.put(ownerAddress,
                  new VoteWitnessTx(txCapsule.getTransactionId(), voteWitnessContract));
            }
          });
        } catch (InvalidProtocolBufferException e) {
          e.printStackTrace();
          System.exit(-1);
        }
      });
    }
  }

  private Boolean existInWitnessList(List<String> witnessList,
      VoteWitnessContract voteWitnessContract) {
    AtomicBoolean exist = new AtomicBoolean(false);
    voteWitnessContract.getVotesList().forEach(vote -> {
      if (witnessList.contains(StringUtil.encode58Check(vote.getVoteAddress().toByteArray()))) {
        exist.set(true);
      }
    });
    return exist.get();
  }

  private class VoteWitnessTx {

    private Sha256Hash txHash;
    private VoteWitnessContract voteWitnessContract;

    VoteWitnessTx(Sha256Hash txHash, VoteWitnessContract voteWitnessContract) {
      this.txHash = txHash;
      this.voteWitnessContract = voteWitnessContract;
    }

    private Boolean existInWitnessList(List<String> witnessList) {
      AtomicBoolean exist = new AtomicBoolean(false);
      voteWitnessContract.getVotesList().forEach(vote -> {
        if (witnessList.contains(StringUtil.encode58Check(vote.getVoteAddress().toByteArray()))) {
          exist.set(true);
        }
      });
      return exist.get();
    }
  }
}
