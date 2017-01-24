package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

/**
  * Nikolay.Tropin
  * 2014-05-06
  */
abstract class ExistsEqualsTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[ExistsEqualsInspection]
}

class ReplaceWithContainsTest extends ExistsEqualsTest {

  override protected val hint: String =
    InspectionBundle.message("exists.equals.hint")

  def test_1() {
    val selected = s"List(0).${START}exists(x => x == 1)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(x => x == 1)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_2() {
    val selected = s"List(0).${START}exists(_ == 1)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(_ == 1)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_3() {
    val selected = s"List(0) ${START}exists (x => x == 1)$END"
    checkTextHasError(selected)
    val text = "List(0) exists (x => x == 1)"
    val result = "List(0) contains 1"
    testQuickFix(text, result, hint)
  }

  def test_4() {
    val selected = s"List(0).${START}exists(1 == _)$END"
    checkTextHasError(selected)
    val text = "List(0).exists(1 == _)"
    val result = "List(0).contains(1)"
    testQuickFix(text, result, hint)
  }

  def test_5() {
    val text = "List(0).exists(x => x == - x)"
    checkTextHasNoErrors(text)
  }

  def test_6() {
    val text = "Some(1).exists(_ == 1)"
    checkTextHasNoErrors(text)
  }

  def test_7() {
    val text = "Map(1 -> \"1\").exists(_ == (1, \"1\"))"
    checkTextHasNoErrors(text)
  }
}

class ReplaceWithNotContainsTest extends ExistsEqualsTest {

  override protected val hint: String =
    InspectionBundle.message("forall.notEquals.hint")

  def testForallNotEquals(): Unit = {
    val selected = s"Seq(1, 2).${START}forall(_ != 2)$END"
    checkTextHasError(selected)
    val text = "Seq(1, 2).forall(_ != 2)"
    val result = "!Seq(1, 2).contains(2)"
    testQuickFix(text, result, hint)
  }
}
