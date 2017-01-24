package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceDoTest

/**
  * Created by Svyatoslav Ilinskiy on 01.07.16.
  */
class PackageTypeInferenceTest extends ScalaLightCodeInsightFixtureTestAdapter with TypeInferenceDoTest {
  def testImplicitPackageObjects(): Unit = {
    val foo =
      """
        |package outer.a.c
        |
        |class Foo
      """.stripMargin
    val packObject =
      """
        |package outer
        |
        |import outer.a.c.Foo
        |
        |package object a {
        |  implicit def string2Foo(s: String): Foo = new Foo
        |}
        |
      """.stripMargin
    val testFile =
      """
        |package b
        |
        |import outer.a.c.Foo
        |
        |object Moo {
        |
        |  def baz(m: Foo): Foo = {
        |    /*start*/"222"/*end*/
        |  }
        |}
        |//Foo
      """.stripMargin

    addFile(foo, "Foo.scala")
    addFile(packObject, "a.scala")
    doTest(Some(testFile))
  }

  protected def addFile(text: String, name: String) = {
    getFixture.addFileToProject(name, text)
  }

  override def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile = {
    getFixture.configureByText(fileName, fileText.get.trim.replace("\r", "")).asInstanceOf[ScalaFile]
  }
}
