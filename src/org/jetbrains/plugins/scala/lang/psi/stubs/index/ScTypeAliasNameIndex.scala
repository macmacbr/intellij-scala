package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScTypeAliasNameIndex extends ScStringStubIndexExtension[ScTypeAlias] {

  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.TYPE_ALIAS_NAME_KEY
}

class ScStableTypeAliasNameIndex extends ScStringStubIndexExtension[ScTypeAlias] {
  override def getKey: StubIndexKey[String, ScTypeAlias] =
    ScalaIndexKeys.STABLE_ALIAS_NAME_KEY
}
