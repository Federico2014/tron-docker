package org.tron.plugins;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.tron.common.utils.Base58;
import org.tron.common.utils.Commons;
import org.tron.core.capsule.VotesCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.plugins.utils.FileUtils;
import org.tron.plugins.utils.JsonFormat;
import org.tron.plugins.utils.db.DBInterface;
import org.tron.plugins.utils.db.DBIterator;
import org.tron.plugins.utils.db.DbTool;
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

  private void initStore() throws IOException, RocksDBException {
    String srcDir = database + File.separator + "database";
    witnessStore = DbTool.getDB(srcDir, WITNESS_STORE);
    votesStore = DbTool.getDB(srcDir, VOTES_STORE);
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

    boolean allWitness = false;
    List<String> witnessList = new ArrayList<>();
    if (queryConfig.hasPath(VOTES_ALL_WITNESSES)) {
      allWitness = queryConfig.getBoolean(VOTES_ALL_WITNESSES);
    }
    if (queryConfig.hasPath(VOTES_WITNESS_LIST)) {
      witnessList = queryConfig.getStringList(VOTES_WITNESS_LIST);
    }

    if (allWitness || witnessList.size() > 0) {
      Map<ByteString, WitnessCapsule> witnesses = new HashMap<>();
      DBIterator iterator = witnessStore.iterator();
      WitnessCapsule witnessCapsule;
      for (iterator.seekToFirst(); iterator.valid(); iterator.next()) {
        witnessCapsule = new WitnessCapsule(iterator.getValue());
        witnesses.put(ByteString.copyFrom(iterator.getKey()), witnessCapsule);
      }

      Map<ByteString, Long> countWitness = countVote(votesStore);
      countWitness.forEach((address, voteCount) -> {
        WitnessCapsule witness = witnesses.get(address);
        if (witness == null) {
          return;
        }
        witness.setVoteCount(witness.getVoteCount() + voteCount);
        witnesses.put(address, witness);
      });

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
              WitnessCapsule witnessTmp =  witnesses.get(address);
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

      witnessCapsuleList.forEach(
          witness -> {
            String witnessStr = JsonFormat.printToString(witness.getInstance(), true);
            spec.commandLine().getOut().println(witnessStr);
            logger.info(witnessStr);
          });
    }

    DbTool.close();
    return 0;
  }

  private Map<ByteString, Long> countVote(DBInterface votesStore) {
    final Map<ByteString, Long> countWitness = Maps.newHashMap();
    DBIterator dbIterator = votesStore.iterator();
    long sizeCount = 0;
    while (dbIterator.hasNext()) {
      Entry<byte[], byte[]> next = dbIterator.next();
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
      sizeCount++;
    }
    spec.commandLine().getOut().format("There is %d new votes in this epoch", sizeCount).println();
    logger.info("There is {} new votes in this epoch", sizeCount);
    return countWitness;
  }

}
