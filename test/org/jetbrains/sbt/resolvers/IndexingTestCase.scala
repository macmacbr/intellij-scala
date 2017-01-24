package org.jetbrains.sbt.resolvers

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.sbt.resolvers.indexes.ResolverIndex
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
abstract class IndexingTestCase extends ScalaLightCodeInsightFixtureTestAdapter {
  
  override def setUp(): Unit = {
    super.setUp()
    System.setProperty("ivy.test.indexes.dir", getFixture.getTempDirPath)
  }

  override def tearDown(): Unit = {
    super.tearDown()
  }

  def assertIndexContentsEquals(index: ResolverIndex, groups: Set[String], artifacts: Set[String], versions: Set[String]): Unit = {
    implicit val p = getProject
    assertEquals(index.searchGroup(), groups)
    assertEquals(index.searchArtifact(), artifacts)
    artifacts foreach { a => assertEquals(index.searchVersion(groups.head, a), versions) }
  }
}
