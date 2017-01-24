package org.jetbrains.plugins.scala
package lang
package transformation
package general

/**
  * @author Pavel Fatin
  */
class AppendSemicolonTest extends TransformerTest(new AppendSemicolon()) {

  def testSingleLineSeparator(): Unit = check(
    before = "A\nB",
    after = "A;\nB;"
  )()

  def testMultipleLineSeparators(): Unit = check(
    before = "A\n\nB",
    after = "A;\n\nB;"
  )()

  def testExplicit(): Unit = check(
    before = "A;\nB;",
    after = "A;\nB;"
  )()
}
