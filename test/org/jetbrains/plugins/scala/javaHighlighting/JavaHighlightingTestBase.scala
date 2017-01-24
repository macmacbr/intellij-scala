package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaLightCodeInsightFixtureTestAdapter}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Alefas
  * @since 23/03/16
  */
abstract class JavaHighlightingTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertMatches {

  override protected def libVersion: ScalaSdkVersion =
    ScalaSdkVersion._2_11

  private var filesCreated: Boolean = false

  def errorsFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    getFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = getFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    getFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = getFixture.doHighlighting()

    filesCreated = true

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  def errorsFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    getFixture.addFileToProject("dummy.java", javaFileText)
    getFixture.configureByText("dummy.scala", scalaFileText)

    filesCreated = true

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock(getFile)
    val annotator = new ScalaAnnotator

    getFile.depthFirst().foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }

  val CannotResolveMethod = ContainsPattern("Cannot resolve method")
  val CannotBeApplied = ContainsPattern("cannot be applied")
  val CannotBeInstantianted = ContainsPattern("is abstract; cannot be instantiated")

  case class ContainsPattern(fragment: String) {
    def unapply(s: String): Boolean = s.contains(fragment)
  }
}
