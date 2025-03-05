package org.tron.plugins;

import java.io.IOException;
import org.junit.Ignore;
import org.junit.Test;


public class DbLiteLevelDbTest  extends DbLiteTest {

  @Ignore
  @Test
  public void testToolsWithLevelDB() throws InterruptedException, IOException {
    testTools("LEVELDB", 1);
  }
}
