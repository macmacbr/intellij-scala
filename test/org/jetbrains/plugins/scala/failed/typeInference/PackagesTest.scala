package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 22/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class PackagesTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL8850(): Unit = {
    getFixture.addFileToProject("tuff/scl8850/temp.txt", "Something")
    getFixture.addFileToProject("scl8850/A.scala",
      """
        |package scl8850
        |
        |object A {
        |  val a = 1
        |}
      """.stripMargin)
    val fileToCheck = getFixture.addFileToProject("tuff/MyTest.scala",
      """
        |package tuff
        |
        |import scl8850._
        |
        |class MyTest {
        |  A.a
        |}
      """.stripMargin)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        assert(ref.resolve() != null, s"Can't resolve reference ${ref.refName}")
      }
    }
    fileToCheck.accept(visitor)
  }
}
