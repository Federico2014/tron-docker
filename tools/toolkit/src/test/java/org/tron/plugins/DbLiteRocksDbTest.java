package org.tron.plugins;

import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;

public class DbLiteRocksDbTest extends DbLiteTest {

  @Ignore
  @Test
  public void testToolsWithRocksDB() throws InterruptedException, IOException {
    testTools("ROCKSDB", 1);
  }
}
