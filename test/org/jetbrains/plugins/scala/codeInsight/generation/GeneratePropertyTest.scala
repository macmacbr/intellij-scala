package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Nikolay.Tropin
 * 2014-09-22
 */
class GeneratePropertyTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGeneratePropertyHandler

  def testSimple() {
    val text = s"""class A {
                 |  ${CARET_MARKER}var a: Int = 0
                 |}"""

    val result = s"""class A {
                   |  private[this] var _a: Int = 0
                   |
                   |  def a: Int = _a
                   |
                   |  def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    performTest(text, result, checkAvailability = true)
  }

  def testWithoutType() {
    val text = s"""object A {
                 |  ${CARET_MARKER}var a = 0
                 |}"""

    val result = s"""object A {
                   |  private[this] var _a: Int = 0
                   |
                   |  def a: Int = _a
                   |
                   |  def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    performTest(text, result, checkAvailability = true)
  }

  def testWithModifiers(): Unit = {
    val text = s"""class A {
                 |  protected ${CARET_MARKER}var a = 0
                 |}"""

    val result = s"""class A {
                   |  private[this] var _a: Int = 0
                   |
                   |  protected def a: Int = _a
                   |
                   |  protected def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    performTest(text, result, checkAvailability = true)
  }
}
