package org.jetbrains.plugins.scala.codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Tests for toString method generation.
 * @author Rado Buransky (buransky.com)
 */
class GenerateToStringTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateToStringHandler

  def testFindAllFields(): Unit = {
    val text = s"""class A (i: Int, val j: Int) {
                 |  val x = 0$CARET_MARKER
                 |  var y = 0
                 |
                 |  def w(x: Int) = 42
                 |
                 |  def v = -3
                 |
                 |  def u() = 123
                 |
                 |  private val z = 0
                 |}"""
    val result = """class A (i: Int, val j: Int) {
                   |  val x = 0
                   |  var y = 0
                   |
                   |  def w(x: Int) = 42
                   |
                   |  def v = -3
                   |
                   |  def u() = 123
                   |
                   |  private val z = 0
                   |
                   |  override def toString = s"A(x=$x, y=$y, z=$z, j=$j, v=$v, u=$u)"
                   |}"""

    performTest(text, result)
  }

  def testEmptyClass(): Unit = {
    val text = s"""class A() {
                 |  $CARET_MARKER
                 |}"""
    val result = """class A() {
                   |
                   |  override def toString = s"A()"
                   |}"""

    performTest(text, result)
  }

  def testObject(): Unit = {
    val text = s"""object Obj {
                 |  val a = 1
                 |
                 |  def b = 2
                 |  $CARET_MARKER
                 |}"""
    val result = """object Obj {
                 |  val a = 1
                 |
                 |  def b = 2
                 |
                 |  override def toString = s"Obj(a=$a, b=$b)"
                 |}"""

    performTest(text, result)
  }

  def testTrait(): Unit = {
    val text = s"""trait T {
                 |  val a = 1
                 |
                 |  def b = 2
                 |  $CARET_MARKER
                 |}"""
    val result = """trait T {
                 |  val a = 1
                 |
                 |  def b = 2
                 |
                 |  override def toString = s"T(a=$a, b=$b)"
                 |}"""

    performTest(text, result)
  }
}
