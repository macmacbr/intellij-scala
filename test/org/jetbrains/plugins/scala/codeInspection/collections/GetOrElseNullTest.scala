package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class GetOrElseNullTest extends OperationsOnCollectionInspectionTest {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val hint: String = InspectionBundle.message("getOrElse.null.hint")

  def test_1() {
    val selected = s"None.${START}getOrElse(null)$END"
    checkTextHasError(selected)

    val text = "None.getOrElse(null)"
    val result = "None.orNull"
    testQuickFix(text, result, hint)
  }

  def test_2() {
    val selected = s"None ${START}getOrElse null$END"
    checkTextHasError(selected)

    val text = "None getOrElse null"
    val result = "None.orNull"
    testQuickFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected = s"Some(1) orElse Some(2) ${START}getOrElse null$END"
    checkTextHasError(selected)

    val text = "Some(1) orElse Some(2) getOrElse null"
    val result = "(Some(1) orElse Some(2)).orNull"
    testQuickFix(text, result, hint)
  }

  override val classOfInspection = classOf[GetOrElseNullInspection]
}
