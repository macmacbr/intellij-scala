package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.MockPsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
abstract class CachedTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  trait Managed {
    val getManager = new MockPsiManager(getProject) {
      override def getModificationTracker: PsiModificationTrackerImpl = {
        super.getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]
      }
    }
  }
}
