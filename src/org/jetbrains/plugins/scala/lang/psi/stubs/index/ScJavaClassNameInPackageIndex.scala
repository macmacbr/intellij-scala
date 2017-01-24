package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

/**
 * User: Alefas
 * Date: 10.02.12
 */
class ScJavaClassNameInPackageIndex extends ScStringStubIndexExtension[PsiClass] {
  override def getKey: StubIndexKey[String, PsiClass] =
    ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY
}
