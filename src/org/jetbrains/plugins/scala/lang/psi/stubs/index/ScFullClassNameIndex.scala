package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

/**
  * @author ilyas
  */
class ScFullClassNameIndex extends ScIntStubIndexExtension[PsiClass] {
  override def getKey: StubIndexKey[Integer, PsiClass] =
    ScalaIndexKeys.FQN_KEY
}
